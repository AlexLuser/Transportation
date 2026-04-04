package com.fm.driver.service;

import com.fm.common.dto.PageResult;
import com.fm.driver.entity.OrderDelivery;

import java.util.List;

/**
 * 配送服务接口
 */
public interface DeliveryService {
    /**
     * 获取待接单订单列表（分页）
     */
    PageResult<OrderDelivery> getPendingDeliveries(Long current, Long size);
    
    /**
     * 获取运输员的配送订单列表（分页，支持状态筛选）
     */
    PageResult<OrderDelivery> getMyDeliveries(Long driverId, Long current, Long size, Integer status, String sortField, String sortOrder);
    
    /**
     * 根据配送ID获取配送详情
     */
    OrderDelivery getDeliveryById(Long deliveryId);
    
    /**
     * 根据订单ID获取配送信息
     */
    OrderDelivery getDeliveryByOrderId(Long orderId);

    /**
     * 当前运输员进行中的配送（已接单、运输中）
     */
    List<OrderDelivery> listInProgressDeliveries(Long driverId);
    
    /**
     * 接单
     */
    OrderDelivery acceptDelivery(Long deliveryId, Long driverId, Long vehicleId);
    
    /**
     * 更新配送状态
     */
    OrderDelivery updateDeliveryStatus(Long deliveryId, Long driverId, Integer status, String remark);
    
    /**
     * 取消配送
     */
    OrderDelivery cancelDelivery(Long deliveryId, Long driverId, String cancelReason);
    
    /**
     * 创建配送记录（订单服务调用）
     */
    OrderDelivery createDelivery(Long orderId, String deliveryAddress, String receiverName, String receiverPhone);
}
















