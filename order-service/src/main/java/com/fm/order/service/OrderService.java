package com.fm.order.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.fm.order.dto.CreateOrderRequestDTO;
import com.fm.order.dto.OrderDetailDTO;
import com.fm.order.entity.Order;

import java.util.List;

/**
 * 订单服务接口
 */
public interface OrderService extends IService<Order> {

    /**
     * 创建订单
     * @param customerId 顾客ID
     * @param request    创建订单请求
     * @return 订单详情
     */
    OrderDetailDTO createOrder(Long customerId, CreateOrderRequestDTO request);

    /**
     * 获取订单详情（含订单项）
     * @param orderId 订单ID
     * @return 订单详情
     */
    OrderDetailDTO getOrderDetail(Long orderId);

    /**
     * 根据顾客ID获取订单列表
     * @param customerId 顾客ID
     * @return 订单列表
     */
    List<Order> getOrdersByCustomerId(Long customerId);

    /**
     * 根据商户ID获取订单列表
     * @param shopId 商户ID
     * @return 订单列表
     */
    List<Order> getOrdersByShopId(Long shopId);

    /**
     * 更新订单状态
     * @param orderId     订单ID
     * @param orderStatus 新的订单状态
     * @return 是否成功
     */
    boolean updateOrderStatus(Long orderId, Integer orderStatus);

    /**
     * 取消订单
     * @param orderId      订单ID
     * @param cancelReason 取消原因
     * @return 是否成功
     */
    boolean cancelOrder(Long orderId, String cancelReason);

    /**
     * 支付订单
     * @param orderId 订单ID
     * @return 是否成功
     */
    boolean payOrder(Long orderId);
}

