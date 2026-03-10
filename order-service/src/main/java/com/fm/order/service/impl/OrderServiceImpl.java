package com.fm.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fm.common.exception.BusinessException;
import com.fm.common.result.Result;
import com.fm.common.result.ResultCode;
import com.fm.order.dto.CreateOrderRequestDTO;
import com.fm.order.dto.OrderDetailDTO;
import com.fm.order.entity.Order;
import com.fm.order.entity.OrderItem;
import com.fm.order.feign.CustomerFeignClient;
import com.fm.order.feign.DriverFeignClient;
import com.fm.order.feign.LogisticsFeignClient;
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
import java.util.Objects;

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

    @Autowired
    private LogisticsFeignClient logisticsFeignClient;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    @Transactional
    public OrderDetailDTO createOrder(Long customerId, CreateOrderRequestDTO request) {
        // 1. 验证顾客信息
        Result<Map<String, Object>> customerResult = customerFeignClient.getCustomer(
                customerId, customerId.toString(), "customer");
        if (customerResult.getCode() != 200 || customerResult.getData() == null) {
            throw new BusinessException(ResultCode.FAIL.getCode(), "顾客信息不存在");
        }

        // 2. 验证收货地址（获取地址列表，然后验证addressId是否存在）
        Result<List<Map<String, Object>>> addressResult = customerFeignClient.getAddresses(
                customerId, customerId.toString(), "customer");
        if (addressResult.getCode() != 200 || addressResult.getData() == null) {
            throw new BusinessException(ResultCode.FAIL.getCode(), "无法获取收货地址列表");
        }

        Long requestAddressId = request.getAddressId();
        if (requestAddressId == null) {
            throw new BusinessException(ResultCode.FAIL.getCode(), "收货地址不能为空");
        }

        boolean addressMatched = false;
        for (Map<String, Object> addr : addressResult.getData()) {
            if (addr == null || addr.get("id") == null) {
                continue;
            }
            try {
                Long addrId = Long.valueOf(addr.get("id").toString());
                if (Objects.equals(addrId, requestAddressId)) {
                    addressMatched = true;
                    break;
                }
            } catch (Exception ignore) {
                // 忽略异常数据，继续匹配下一条
            }
        }
        if (!addressMatched) {
            throw new BusinessException(ResultCode.FAIL.getCode(), "收货地址不存在或不属于当前用户");
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
        order.setOrderStatus(0); // 待支付
        order.setPaymentStatus(0); // 未支付
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

        // 根据状态设置相应的时间字段
        if (orderStatus == 3) { // 已发货
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

                    // 调用物流服务创建物流路线（发货地=仓库，收货地=买家地址）
                    try {
                        String startAddress = "未知仓库地址";
                        Double startLat = null;
                        Double startLng = null;
                        if (order.getWarehouseId() != null) {
                            Result<Map<String, Object>> warehouseResult =
                                    shopFeignClient.getWarehouseById(order.getWarehouseId());
                            if (warehouseResult.getCode() == 200 && warehouseResult.getData() != null) {
                                Map<String, Object> wh = warehouseResult.getData();
                                StringBuilder whAddr = new StringBuilder();
                                if (wh.get("province") != null) whAddr.append(wh.get("province"));
                                if (wh.get("city") != null) whAddr.append(wh.get("city"));
                                if (wh.get("district") != null) whAddr.append(wh.get("district"));
                                if (wh.get("detailAddress") != null) whAddr.append(wh.get("detailAddress"));
                                startAddress = whAddr.toString();
                                if (wh.get("latitude") != null) {
                                    startLat = Double.valueOf(wh.get("latitude").toString());
                                }
                                if (wh.get("longitude") != null) {
                                    startLng = Double.valueOf(wh.get("longitude").toString());
                                }
                            }
                        }
                        java.util.Map<String, Object> routeRequest = new java.util.HashMap<>();
                        routeRequest.put("orderId", orderId);
                        routeRequest.put("warehouseId", order.getWarehouseId());
                        routeRequest.put("startAddress", startAddress);
                        routeRequest.put("startLat", startLat);
                        routeRequest.put("startLng", startLng);
                        routeRequest.put("endAddress", fullAddress.toString());
                        routeRequest.put("receiverName", receiverName);
                        routeRequest.put("receiverPhone", receiverPhone);
                        Result<Map<String, Object>> routeResult = logisticsFeignClient.createRoute(routeRequest);
                        if (routeResult.getCode() != 200) {
                            System.err.println("创建物流路线失败: " + routeResult.getMessage());
                        }
                    } catch (Exception le) {
                        System.err.println("创建物流路线异常: " + le.getMessage());
                    }
                } else {
                    System.err.println("获取地址信息失败，无法创建配送记录");
                }
            } catch (Exception e) {
                // 记录日志，但不影响订单状态更新
                System.err.println("创建配送记录异常: " + e.getMessage());
                e.printStackTrace();
            }
        } else if (orderStatus == 4) { // 已完成
            wrapper.set(Order::getCompleteTime, new Date());
        }

        return orderMapper.update(null, wrapper) > 0;
    }

    @Override
    @Transactional
    public boolean cancelOrder(Long orderId, String cancelReason) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(ResultCode.FAIL.getCode(), "订单不存在");
        }

        // 只有待支付和已支付的订单可以取消
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
