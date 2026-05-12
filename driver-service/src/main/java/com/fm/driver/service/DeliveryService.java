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

    /**
     * 干线司机确认到达 Hub 中转站（Hub-and-Spoke 专用）
     * 触发末端路线激活流程
     */
    OrderDelivery arriveAtHub(Long deliveryId, Long driverId);

    /**
     * 司机主动接单附近路线段（智能调度模式）
     *
     * 与传统 acceptDelivery 不同：不需要预先存在 OrderDelivery 记录，
     * 司机从附近路线段列表中选择一条，直接创建配送单并完成路线绑定。
     *
     * @param routeId      目标路线段ID（logistics_route.id）
     * @param driverId     司机ID
     * @param vehicleId    使用车辆ID
     * @param orderId      关联订单ID
     * @param startAddress 路线段起点地址
     * @param endAddress   路线段终点地址
     * @param receiverName 收货人（末端段）
     * @param receiverPhone 收货电话（末端段）
     * @param routeType    路线类型（0=直送 1=Hub干线 2=末端）
     * @param batchId      批次ID（可null）
     * @param hubId        中转站ID（可null）
     * @return 创建的配送记录
     */
    OrderDelivery acceptSegment(Long routeId, Long driverId, Long vehicleId,
                                Long orderId, String startAddress, String endAddress,
                                String receiverName, String receiverPhone,
                                Integer routeType, Long batchId, Long hubId);
}
















