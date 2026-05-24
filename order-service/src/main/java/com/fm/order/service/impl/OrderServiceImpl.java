package com.fm.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fm.common.dto.PageResult;
import com.fm.common.exception.BusinessException;
import com.fm.common.result.Result;
import com.fm.common.result.ResultCode;
import com.fm.order.config.RabbitMQConfig;
import com.fm.order.dto.CreateOrderRequestDTO;
import com.fm.order.dto.OrderDetailDTO;
import com.fm.order.entity.Order;
import com.fm.order.entity.OrderItem;
import com.fm.order.feign.CustomerFeignClient;
import com.fm.order.feign.LogisticsFeignClient;
import com.fm.order.feign.ShopFeignClient;
import com.fm.order.mapper.OrderItemMapper;
import com.fm.order.mapper.OrderMapper;
import com.fm.order.mq.AddToPoolMessage;
import com.fm.order.mq.CreateDeliveryMessage;
import com.fm.order.mq.StockDeductMessage;
import com.fm.order.service.OrderService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 订单服务实现类
 * 提供订单信息的业务逻辑实现
 */
@Service
public class OrderServiceImpl extends ServiceImpl<OrderMapper, Order> implements OrderService {

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private OrderItemMapper orderItemMapper;

    @Autowired
    private CustomerFeignClient customerFeignClient;

    @Autowired
    private ShopFeignClient shopFeignClient;

    @Autowired
    private LogisticsFeignClient logisticsFeignClient;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    @Transactional
    public OrderDetailDTO createOrder(Long customerId, CreateOrderRequestDTO request) {
        // 注意：customerId 已由 Controller 层通过内部接口转换（userId → customerId），此处直接使用。

        // 1. 验证收货地址（通过 addressId 直接查询，并校验归属）
        Long requestAddressId = request.getAddressId();
        if (requestAddressId == null) {
            throw new BusinessException(ResultCode.FAIL.getCode(), "收货地址不能为空");
        }
        Result<Map<String, Object>> addrByIdResult = customerFeignClient.getAddressById(requestAddressId);
        if (addrByIdResult.getCode() != 200 || addrByIdResult.getData() == null) {
            throw new BusinessException(ResultCode.FAIL.getCode(), "收货地址不存在");
        }
        Map<String, Object> addrData = addrByIdResult.getData();
        // 校验地址归属：地址的 customerId 必须与当前登录顾客一致
        if (addrData.get("customerId") != null) {
            Long addrCustomerId = Long.valueOf(addrData.get("customerId").toString());
            if (!addrCustomerId.equals(customerId)) {
                throw new BusinessException(ResultCode.FAIL.getCode(), "收货地址不属于当前顾客");
            }
        }

        // 2. 验证商品信息并计算金额
        // 收集待扣减的库存信息，在订单创建后通过 MQ 异步扣减（最终一致性）
        BigDecimal productAmount = BigDecimal.ZERO;
        List<OrderItem> orderItems = new ArrayList<>();
        List<StockDeductMessage> pendingDeductions = new ArrayList<>();
        Long firstWarehouseId = null;  // 记录第一个仓库（作为发货仓库）

        for (CreateOrderRequestDTO.OrderItemDTO itemDTO : request.getItems()) {
            // 获取商品信息（同步 Feign 查询，需要商品数据构建订单）
            Result<Map<String, Object>> productResult = shopFeignClient.getProduct(itemDTO.getProductId());
            if (productResult.getCode() != 200 || productResult.getData() == null) {
                throw new BusinessException(ResultCode.FAIL.getCode(), "商品不存在：ID=" + itemDTO.getProductId());
            }

            Map<String, Object> productData = productResult.getData();
            Long productShopId = Long.valueOf(productData.get("shopId").toString());

            // 验证商品是否属于指定商户
            if (!productShopId.equals(request.getShopId())) {
                throw new BusinessException(ResultCode.FAIL.getCode(), "商品不属于该商户");
            }

            // 验证商品状态（必须上架）
            Integer productStatus = Integer.valueOf(productData.get("status").toString());
            if (productStatus != 1) {
                throw new BusinessException(ResultCode.FAIL.getCode(), "商品已下架或待审核");
            }

            // 查询库存（同步 Feign，用于下单前的库存预检）
            Result<List<Map<String, Object>>> stockResult = shopFeignClient.getStockByProduct(itemDTO.getProductId());
            if (stockResult.getCode() != 200 || stockResult.getData() == null || stockResult.getData().isEmpty()) {
                throw new BusinessException(ResultCode.FAIL.getCode(), "商品库存不足：商品ID=" + itemDTO.getProductId());
            }

            List<Map<String, Object>> stockList = stockResult.getData();

            // 找到库存充足的仓库
            Long warehouseId = null;
            for (Map<String, Object> stock : stockList) {
                Integer availableStock = Integer.valueOf(stock.get("stock").toString());
                if (availableStock >= itemDTO.getQuantity()) {
                    warehouseId = Long.valueOf(stock.get("warehouseId").toString());
                    break;
                }
            }

            if (warehouseId == null) {
                throw new BusinessException(ResultCode.FAIL.getCode(),
                    "商品库存不足：商品ID=" + itemDTO.getProductId() + "，需要数量=" + itemDTO.getQuantity());
            }

            // #6: 不再同步扣减，改为收集扣减信息，订单落库后通过 MQ 异步发送
            // 说明：库存预检已确认充足，正式扣减采用最终一致性方案；
            //       若扣减失败（极低概率竞态），消费者重试 3 次后进入死信队列由人工介入。
            pendingDeductions.add(new StockDeductMessage(null, warehouseId, itemDTO.getProductId(), itemDTO.getQuantity()));

            // 记录第一个发货仓库（作为物流路线的出发地）
            if (firstWarehouseId == null) {
                firstWarehouseId = warehouseId;
            }

            // 计算小计
            BigDecimal price = new BigDecimal(productData.get("price").toString());
            BigDecimal subtotal = price.multiply(new BigDecimal(itemDTO.getQuantity()));
            productAmount = productAmount.add(subtotal);

            // 创建订单项
            OrderItem orderItem = new OrderItem();
            orderItem.setProductId(itemDTO.getProductId());
            orderItem.setProductName(productData.get("productName").toString());
            // 处理商品图片（JSON数组格式，取第一张）
            String images = productData.get("images") != null ? productData.get("images").toString() : null;
            if (images != null && images.startsWith("[")) {
                try {
                    List<String> imageList = objectMapper.readValue(images, new TypeReference<List<String>>() {});
                    if (imageList != null && !imageList.isEmpty()) {
                        orderItem.setProductImage(imageList.get(0));
                    }
                } catch (Exception e) {
                    // 解析失败，忽略
                }
            }
            orderItem.setProductPrice(price);
            orderItem.setQuantity(itemDTO.getQuantity());
            orderItem.setSubtotal(subtotal);
            orderItems.add(orderItem);
        }

        // 3. 生成订单号
        String orderNo = generateOrderNo();

        // 4. 创建订单
        Order order = new Order();
        order.setOrderNo(orderNo);
        order.setCustomerId(customerId);
        order.setShopId(request.getShopId());
        order.setAddressId(request.getAddressId());
        order.setProductAmount(productAmount);
        order.setShippingFee(BigDecimal.ZERO); // 运费暂时为0
        order.setWarehouseId(firstWarehouseId);  // 发货仓库（物流路线起点）
        order.setTotalAmount(productAmount.add(order.getShippingFee()));
        // 毕设：无真实支付流程，下单即视为已支付（paymentStatus=1）、进入待发货状态（orderStatus=1）
        order.setOrderStatus(1);   // 待发货
        order.setPaymentStatus(1); // 已支付
        order.setPaymentTime(new Date());
        order.setRemark(request.getRemark());

        orderMapper.insert(order);

        // 5. 创建订单项
        for (OrderItem orderItem : orderItems) {
            orderItem.setOrderId(order.getId());
            orderItemMapper.insert(orderItem);
        }

        // 6. 订单落库成功后，通过 MQ 异步发送库存扣减消息（#6）
        for (StockDeductMessage msg : pendingDeductions) {
            msg.setOrderId(order.getId());
            rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.ROUTING_STOCK_DEDUCT, msg);
        }

        // 7. 返回订单详情
        OrderDetailDTO orderDetail = new OrderDetailDTO();
        orderDetail.setOrder(order);
        orderDetail.setItems(orderItems);

        return orderDetail;
    }

    @Override
    public OrderDetailDTO getOrderDetail(Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            return null;
        }

        LambdaQueryWrapper<OrderItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrderItem::getOrderId, orderId);
        List<OrderItem> items = orderItemMapper.selectList(wrapper);

        OrderDetailDTO orderDetail = new OrderDetailDTO();
        orderDetail.setOrder(order);
        orderDetail.setItems(items);

        return orderDetail;
    }

    @Override
    public List<Order> getOrdersByCustomerId(Long customerId) {
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Order::getCustomerId, customerId)
                .ne(Order::getCustomerDeleted, 1)
                .orderByDesc(Order::getCreateTime);
        return orderMapper.selectList(wrapper);
    }

    @Override
    public List<Order> getOrdersByShopId(Long shopId) {
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Order::getShopId, shopId)
                .orderByDesc(Order::getCreateTime);
        return orderMapper.selectList(wrapper);
    }

    @Override
    @Transactional
    public boolean updateOrderStatus(Long orderId, Integer orderStatus) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(ResultCode.FAIL.getCode(), "订单不存在");
        }

        LambdaUpdateWrapper<Order> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(Order::getId, orderId).set(Order::getOrderStatus, orderStatus);

        // 订单状态：0=待支付，1=待发货，2=待揽件，3=派送中，4=已完成，5=已取消，6=待签收
        if (orderStatus == 2) { // 待揽件（商户发货）
            // B3：发货前置校验 - 订单必须是已支付(paymentStatus=1)且处于待发货状态(orderStatus=1)
            if (!Integer.valueOf(1).equals(order.getPaymentStatus())) {
                throw new BusinessException(ResultCode.FAIL.getCode(), "订单未支付，不能发货");
            }
            if (!Integer.valueOf(1).equals(order.getOrderStatus())) {
                throw new BusinessException(ResultCode.FAIL.getCode(), "订单当前状态不允许发货（当前状态：" + order.getOrderStatus() + "，需为待发货:1）");
            }

            wrapper.set(Order::getShippingTime, new Date());
            
            // #8a: 商户备货完成 → 订单进入调度池，由智能调度系统统一处理
            try {
                Result<Map<String, Object>> addressResult = customerFeignClient.getAddressById(order.getAddressId());
                if (addressResult.getCode() == 200 && addressResult.getData() != null) {
                    Map<String, Object> addressData = addressResult.getData();
                    String receiverName  = addressData.get("receiverName")  != null ? addressData.get("receiverName").toString()  : "";
                    String receiverPhone = addressData.get("receiverPhone") != null ? addressData.get("receiverPhone").toString() : "";

                    StringBuilder fullAddress = new StringBuilder();
                    if (addressData.get("province")     != null) fullAddress.append(addressData.get("province").toString());
                    if (addressData.get("city")         != null) fullAddress.append(addressData.get("city").toString());
                    if (addressData.get("district")     != null) fullAddress.append(addressData.get("district").toString());
                    if (addressData.get("detailAddress") != null) fullAddress.append(addressData.get("detailAddress").toString());

                    Double endLat = null, endLng = null;
                    if (addressData.get("latitude")  != null) endLat = Double.parseDouble(addressData.get("latitude").toString());
                    if (addressData.get("longitude") != null) endLng = Double.parseDouble(addressData.get("longitude").toString());

                    Long oh = order.getOriginHubId();
                    Long dh = order.getDestHubId();
                    boolean poolCrossCity = oh != null && dh != null && !oh.equals(dh);
                    AddToPoolMessage poolMsg = new AddToPoolMessage(
                        orderId,
                        order.getShopId(),
                        order.getWarehouseId(),
                        fullAddress.toString(),
                        endLat, endLng,
                        receiverName, receiverPhone,
                        order.getRemark(),
                        oh,
                        dh,
                        poolCrossCity
                    );
                    rabbitTemplate.convertAndSend(
                        RabbitMQConfig.EXCHANGE,
                        RabbitMQConfig.ROUTING_ADD_TO_POOL,
                        poolMsg
                    );
                    System.out.println("订单 " + orderId + " 已投入调度池，等待智能调度");
                } else {
                    System.err.println("获取地址信息失败，订单无法进入调度池: orderId=" + orderId);
                }
            } catch (Exception e) {
                System.err.println("发送调度池消息异常: " + e.getMessage());
                e.printStackTrace();
            }
        } else if (orderStatus == 3) { // 派送中（运输员接单后）
            if (!Integer.valueOf(2).equals(order.getOrderStatus())) {
                throw new BusinessException(ResultCode.FAIL.getCode(),
                        "订单当前状态不允许变为派送中（需为待揽件:2，当前:" + order.getOrderStatus() + "）");
            }
        } else if (orderStatus == 6) { // 待签收（司机确认送达，等待顾客签收）
            if (!Integer.valueOf(3).equals(order.getOrderStatus())) {
                throw new BusinessException(ResultCode.FAIL.getCode(),
                        "订单当前状态不允许进入待签收（需为派送中:3，当前:" + order.getOrderStatus() + "）");
            }
        } else if (orderStatus == 4) { // 已完成（顾客签收 or 管理员强制完成）
            // 允许从 待签收(6) 正常签收，也允许管理员从 派送中(3) 直接强制完成
            if (!Integer.valueOf(6).equals(order.getOrderStatus())
                    && !Integer.valueOf(3).equals(order.getOrderStatus())) {
                throw new BusinessException(ResultCode.FAIL.getCode(),
                        "订单当前状态不允许完成（需为待签收:6 或派送中:3，当前:" + order.getOrderStatus() + "）");
            }
            wrapper.set(Order::getCompleteTime, new Date());
        }

        return orderMapper.update(null, wrapper) > 0;
    }

    @Override
    @Transactional
    public boolean signOrder(Long orderId, Long customerId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(ResultCode.FAIL.getCode(), "订单不存在");
        }
        if (!order.getCustomerId().equals(customerId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权签收其他顾客的订单");
        }
        if (!Integer.valueOf(6).equals(order.getOrderStatus())) {
            throw new BusinessException(ResultCode.FAIL.getCode(),
                    "订单当前状态不允许签收（需为待签收:6，当前:" + order.getOrderStatus() + "）");
        }
        LambdaUpdateWrapper<Order> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(Order::getId, orderId)
               .set(Order::getOrderStatus, 4)
               .set(Order::getCompleteTime, new Date());
        return orderMapper.update(null, wrapper) > 0;
    }

    @Override
    @Transactional
    public boolean reopenOrderToPendingPickup(Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(ResultCode.FAIL.getCode(), "订单不存在");
        }
        if (!Integer.valueOf(3).equals(order.getOrderStatus())) {
            throw new BusinessException(ResultCode.FAIL.getCode(),
                    "仅派送中订单可回到待揽件（当前:" + order.getOrderStatus() + "）");
        }
        LambdaUpdateWrapper<Order> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(Order::getId, orderId).set(Order::getOrderStatus, 2);
        return orderMapper.update(null, wrapper) > 0;
    }

    @Override
    @Transactional
    public boolean cancelOrder(Long orderId, String cancelReason) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(ResultCode.FAIL.getCode(), "订单不存在");
        }

        // 只有待支付（0）和待发货（1）的订单可以取消
        if (order.getOrderStatus() != 0 && order.getOrderStatus() != 1) {
            throw new BusinessException(ResultCode.FAIL.getCode(), "当前订单状态不允许取消");
        }

        LambdaUpdateWrapper<Order> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(Order::getId, orderId)
                .set(Order::getOrderStatus, 5) // 已取消
                .set(Order::getCancelTime, new Date())
                .set(Order::getCancelReason, cancelReason);

        return orderMapper.update(null, wrapper) > 0;
    }

    @Override
    @Transactional
    public boolean payOrder(Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(ResultCode.FAIL.getCode(), "订单不存在");
        }

        if (order.getPaymentStatus() == 1) {
            throw new BusinessException(ResultCode.FAIL.getCode(), "订单已支付");
        }

        LambdaUpdateWrapper<Order> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(Order::getId, orderId)
                .set(Order::getPaymentStatus, 1) // 已支付
                .set(Order::getOrderStatus, 1) // 待发货
                .set(Order::getPaymentTime, new Date());

        return orderMapper.update(null, wrapper) > 0;
    }

    @Override
    @Transactional
    public boolean deleteOrderByCustomer(Long orderId, Long customerId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(ResultCode.FAIL.getCode(), "订单不存在");
        }
        if (!order.getCustomerId().equals(customerId)) {
            throw new BusinessException(ResultCode.FAIL.getCode(), "无权操作该订单");
        }
        if (order.getOrderStatus() != 5) {
            throw new BusinessException(ResultCode.FAIL.getCode(), "只有已取消的订单可以删除");
        }
        LambdaUpdateWrapper<Order> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(Order::getId, orderId)
                .set(Order::getCustomerDeleted, 1);
        return orderMapper.update(null, wrapper) > 0;
    }

    @Override
    public PageResult<Order> getAllOrders(Long current, Long size, Integer status) {
        Page<Order> page = new Page<>(current, size);
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            wrapper.eq(Order::getOrderStatus, status);
        }
        wrapper.orderByDesc(Order::getCreateTime);
        IPage<Order> result = orderMapper.selectPage(page, wrapper);
        return new PageResult<>(result.getCurrent(), result.getSize(), result.getTotal(), result.getRecords());
    }

    @Override
    public Order getOrderByNo(String orderNo) {
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Order::getOrderNo, orderNo);
        return orderMapper.selectOne(wrapper);
    }

    @Override
    @Transactional
    public boolean shipOrder(Long orderId, Long shopId, Long warehouseId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(ResultCode.FAIL.getCode(), "订单不存在");
        }
        if (!order.getShopId().equals(shopId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权操作其他商户的订单");
        }
        if (order.getOrderStatus() != 1 || order.getPaymentStatus() != 1) {
            throw new BusinessException(ResultCode.FAIL.getCode(), "订单状态不允许发货（需已支付且处于待发货）");
        }

        // 获取收货地址坐标
        Result<Map<String, Object>> addrResult = customerFeignClient.getAddressById(order.getAddressId());
        Map<String, Object> addr = addrResult.getData();
        Double endLat = addr.get("latitude") != null ? Double.valueOf(addr.get("latitude").toString()) : null;
        Double endLng = addr.get("longitude") != null ? Double.valueOf(addr.get("longitude").toString()) : null;

        // 调 logistics-service 分配 Hub
        Long originHubId = null;
        Long destHubId = null;
        boolean crossCity = false;
        try {
            Map<String, Object> req = new java.util.HashMap<>();
            req.put("warehouseId", warehouseId);
            req.put("endLat", endLat);
            req.put("endLng", endLng);
            Result<Map<String, Object>> hubResult = logisticsFeignClient.assignHubs(req);
            if (hubResult.getCode() == 200 && hubResult.getData() != null) {
                Map<String, Object> hubData = hubResult.getData();
                originHubId = hubData.get("originHubId") != null
                        ? Long.valueOf(hubData.get("originHubId").toString()) : null;
                destHubId = hubData.get("destHubId") != null
                        ? Long.valueOf(hubData.get("destHubId").toString()) : null;
                crossCity = Boolean.parseBoolean(String.valueOf(hubData.getOrDefault("crossCity", false)));
            }
        } catch (Exception e) {
            // Hub 分配失败时降级处理：仍发货，但不写 Hub（同城模式）
            log.warn("[shipOrder] Hub分配失败，降级为同城模式: {}", e.getMessage());
        }

        // 更新 order_info
        LambdaUpdateWrapper<Order> uw = new LambdaUpdateWrapper<>();
        uw.eq(Order::getId, orderId)
          .set(Order::getWarehouseId, warehouseId)
          .set(Order::getOriginHubId, originHubId)
          .set(Order::getDestHubId, destHubId)
          .set(Order::getOrderStatus, 2)
          .set(Order::getShippingTime, new Date());
        orderMapper.update(null, uw);

        // 发 MQ 通知 logistics-service 入调度池
        AddToPoolMessage msg = new AddToPoolMessage();
        msg.setOrderId(orderId);
        msg.setShopId(shopId);
        msg.setWarehouseId(warehouseId);
        StringBuilder endAddr = new StringBuilder();
        if (addr.get("province") != null) {
            endAddr.append(addr.get("province").toString());
        }
        if (addr.get("city") != null) {
            endAddr.append(addr.get("city").toString());
        }
        if (addr.get("district") != null) {
            endAddr.append(addr.get("district").toString());
        }
        if (addr.get("detailAddress") != null) {
            endAddr.append(addr.get("detailAddress").toString());
        }
        msg.setEndAddress(endAddr.toString());
        msg.setEndLat(endLat);
        msg.setEndLng(endLng);
        msg.setReceiverName(addr.get("receiverName") != null ? addr.get("receiverName").toString() : "");
        msg.setReceiverPhone(addr.get("receiverPhone") != null ? addr.get("receiverPhone").toString() : "");
        msg.setRemark(order.getRemark());
        msg.setOriginHubId(originHubId);
        msg.setDestHubId(destHubId);
        msg.setCrossCity(crossCity);

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE,
                RabbitMQConfig.ROUTING_ADD_TO_POOL,
                msg);

        // 自动创建并完成出库单（失败不影响发货主流程）
        try {
            autoCreateOutboundOrder(orderId, shopId, warehouseId, order.getRemark());
        } catch (Exception e) {
            log.warn("[shipOrder] 自动出库单创建失败，订单仍正常发货: orderId={}, error={}", orderId, e.getMessage());
        }

        return true;
    }

    /**
     * 发货时自动创建并完成出库单，触发库存扣减。
     * 出库单类型为 SALES_ORDER，关联 orderId；明细从 order_item 读取。
     */
    private void autoCreateOutboundOrder(Long orderId, Long shopId, Long warehouseId, String remark) {
        // 查询订单明细
        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, orderId));
        if (items == null || items.isEmpty()) {
            log.warn("[autoCreateOutboundOrder] 订单 {} 无明细，跳过出库单创建", orderId);
            return;
        }

        // 构造出库单 body
        List<Map<String, Object>> itemList = new ArrayList<>();
        for (OrderItem oi : items) {
            Map<String, Object> itemMap = new java.util.HashMap<>();
            itemMap.put("productId", oi.getProductId());
            itemMap.put("productName", oi.getProductName());
            itemMap.put("quantity", oi.getQuantity());
            itemList.add(itemMap);
        }

        Map<String, Object> body = new java.util.HashMap<>();
        body.put("warehouseId", warehouseId);
        body.put("shopId", shopId);
        body.put("destType", "SALES_ORDER");
        body.put("relatedId", orderId);
        body.put("remark", "订单 #" + orderId + " 发货自动生成" + (remark != null && !remark.isEmpty() ? "：" + remark : ""));
        body.put("items", itemList);

        // 创建出库单
        Result<Map<String, Object>> createResult = shopFeignClient.createOutboundOrder(body);
        if (createResult == null || createResult.getCode() != 200 || createResult.getData() == null) {
            log.warn("[autoCreateOutboundOrder] 创建出库单失败: orderId={}", orderId);
            return;
        }
        Object idObj = createResult.getData().get("id");
        if (idObj == null) return;
        Long outboundId = Long.valueOf(idObj.toString());

        // 完成出库（扣减库存）
        shopFeignClient.completeOutboundOrder(outboundId);
        log.info("[autoCreateOutboundOrder] 出库单已创建并完成: outboundOrderId={}, orderId={}", outboundId, orderId);
    }

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(OrderServiceImpl.class);

    /**
     * 生成订单号
     * 格式：ORD + yyyyMMddHHmmss + 4位随机数
     */
    private String generateOrderNo() {
        String dateTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        int random = (int) (Math.random() * 10000);
        return "ORD" + dateTime + String.format("%04d", random);
    }
}
