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
import com.fm.order.dto.CreateOrderRequestDTO;
import com.fm.order.dto.OrderDetailDTO;
import com.fm.order.entity.Order;
import com.fm.order.entity.OrderItem;
import com.fm.order.feign.CustomerFeignClient;
import com.fm.order.feign.DriverFeignClient;
import com.fm.order.feign.ShopFeignClient;
import com.fm.order.mapper.OrderItemMapper;
import com.fm.order.mapper.OrderMapper;
import com.fm.order.service.OrderService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private DriverFeignClient driverFeignClient;

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

        // 3. 验证商品信息并计算金额
        BigDecimal productAmount = BigDecimal.ZERO;
        List<OrderItem> orderItems = new ArrayList<>();
        Long firstWarehouseId = null;  // 记录第一个仓库（作为发货仓库）

        for (CreateOrderRequestDTO.OrderItemDTO itemDTO : request.getItems()) {
            // 获取商品信息
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

            // 检查并扣减库存
            Result<List<Map<String, Object>>> stockResult = shopFeignClient.getStockByProduct(itemDTO.getProductId());
            if (stockResult.getCode() != 200 || stockResult.getData() == null || stockResult.getData().isEmpty()) {
                throw new BusinessException(ResultCode.FAIL.getCode(), "商品库存不足：商品ID=" + itemDTO.getProductId());
            }
            
            List<Map<String, Object>> stockList = stockResult.getData();
            
            // 查找有足够库存的仓库
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
            
            // 扣减库存（原子操作）
            Result<Boolean> deductResult = shopFeignClient.deductStock(warehouseId, itemDTO.getProductId(), itemDTO.getQuantity());
            if (deductResult.getCode() != 200 || !Boolean.TRUE.equals(deductResult.getData())) {
                throw new BusinessException(ResultCode.FAIL.getCode(), 
                    "库存扣减失败：商品ID=" + itemDTO.getProductId() + "，可能库存已被其他订单占用");
            }
            
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

        // 4. 生成订单号
        String orderNo = generateOrderNo();

        // 5. 创建订单
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

        // 6. 创建订单项
        for (OrderItem orderItem : orderItems) {
            orderItem.setOrderId(order.getId());
            orderItemMapper.insert(orderItem);
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

        // 订单状态：0=待支付，1=待发货，2=待揽件（商家已发货、待运输员接单），3=派送中，4=已完成，5=已取消
        if (orderStatus == 2) { // 待揽件（商户发货）
            // B3：发货前置校验 - 订单必须是已支付(paymentStatus=1)且处于待发货状态(orderStatus=1)
            if (!Integer.valueOf(1).equals(order.getPaymentStatus())) {
                throw new BusinessException(ResultCode.FAIL.getCode(), "订单未支付，不能发货");
            }
            if (!Integer.valueOf(1).equals(order.getOrderStatus())) {
                throw new BusinessException(ResultCode.FAIL.getCode(), "订单当前状态不允许发货（当前状态：" + order.getOrderStatus() + "，需为待发货:1）");
            }

            wrapper.set(Order::getShippingTime, new Date());
            
            // 创建配送记录
            try {
                // 获取收货地址信息
                Result<Map<String, Object>> addressResult = customerFeignClient.getAddressById(order.getAddressId());
                if (addressResult.getCode() == 200 && addressResult.getData() != null) {
                    Map<String, Object> addressData = addressResult.getData();
                    String receiverName = addressData.get("receiverName") != null ? 
                        addressData.get("receiverName").toString() : "";
                    String receiverPhone = addressData.get("receiverPhone") != null ? 
                        addressData.get("receiverPhone").toString() : "";
                    
                    // 拼接完整地址
                    StringBuilder fullAddress = new StringBuilder();
                    if (addressData.get("province") != null) {
                        fullAddress.append(addressData.get("province").toString());
                    }
                    if (addressData.get("city") != null) {
                        fullAddress.append(addressData.get("city").toString());
                    }
                    if (addressData.get("district") != null) {
                        fullAddress.append(addressData.get("district").toString());
                    }
                    if (addressData.get("detailAddress") != null) {
                        fullAddress.append(addressData.get("detailAddress").toString());
                    }
                    
                    // 调用运输员服务创建配送记录
                    Result<Map<String, Object>> deliveryResult = driverFeignClient.createDelivery(
                        orderId, 
                        fullAddress.toString(), 
                        receiverName, 
                        receiverPhone
                    );
                    
                    if (deliveryResult.getCode() != 200) {
                        // 记录日志，但不影响订单状态更新
                        System.err.println("创建配送记录失败: " + deliveryResult.getMessage());
                    }

                    // 物流路线由商户端在订单状态更新成功后调用 logistics-service 创建（含 LLM 规划与前端展示），
                    // 此处不再重复调用，避免与网关并发产生双次规划与幂等冲突。
                } else {
                    System.err.println("获取地址信息失败，无法创建配送记录");
                }
            } catch (Exception e) {
                // 记录日志，但不影响订单状态更新
                System.err.println("创建配送记录异常: " + e.getMessage());
                e.printStackTrace();
            }
        } else if (orderStatus == 3) { // 派送中（运输员接单后）
            if (!Integer.valueOf(2).equals(order.getOrderStatus())) {
                throw new BusinessException(ResultCode.FAIL.getCode(),
                        "订单当前状态不允许变为派送中（需为待揽件:2，当前:" + order.getOrderStatus() + "）");
            }
        } else if (orderStatus == 4) { // 已完成
            if (!Integer.valueOf(3).equals(order.getOrderStatus())) {
                throw new BusinessException(ResultCode.FAIL.getCode(),
                        "订单当前状态不允许完成（需为派送中:3，当前:" + order.getOrderStatus() + "）");
            }
            wrapper.set(Order::getCompleteTime, new Date());
        }

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
