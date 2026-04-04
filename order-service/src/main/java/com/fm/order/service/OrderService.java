package com.fm.order.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.fm.common.dto.PageResult;
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
     * 运输员取消接单后，将订单从「派送中」回到「待揽件」（仅服务间/管理员调用）
     */
    boolean reopenOrderToPendingPickup(Long orderId);

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

    /**
     * 顾客软删除订单（仅对顾客隐藏，其他角色仍可见）
     * @param orderId    订单ID
     * @param customerId 当前顾客ID（用于鉴权）
     * @return 是否成功
     */
    boolean deleteOrderByCustomer(Long orderId, Long customerId);

    /**
     * 管理员分页查询全部订单（按创建时间倒序），可按状态过滤
     * @param current 页码（从1开始）
     * @param size    每页大小
     * @param status  订单状态过滤（null=全部）
     * @return 分页结果
     */
    PageResult<Order> getAllOrders(Long current, Long size, Integer status);

    /**
     * 根据订单号精确查询订单（管理员用）
     * @param orderNo 订单号
     * @return 订单，不存在返回 null
     */
    Order getOrderByNo(String orderNo);
}

