package com.fm.driver.service;

import com.fm.common.dto.PageResult;
import com.fm.driver.entity.OrderDelivery;

import java.util.List;

/**
 * 配送服务接口
 */
public interface DeliveryService {
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
     * 管理员直接为已存在的 order_delivery 指派司机（直送批次专用）
     *
     * 按 routeId 查找 deliveryStatus=0 的配送记录并直接设置司机（status→1）。
     * 适用于 useHub=false 批次——此类批次的末端路线创建时 order_delivery 已提前生成。
     *
     * @return true 表示找到并更新成功；false 表示未找到（应走 preAssignDriver 流程）
     */
    boolean assignExistingDeliveryByRoute(Long routeId, Long driverId);

    /**
     * 管理员指派干线司机（Hub-and-Spoke 专用）
     *
     * 找到 batchId 对应的干线待接单配送记录，直接指定司机并推进到已接单状态，
     * 同时触发 MQ #10（绑定物流路线）。不修改末端记录——末端预分配通过 logistics_route.driverId 实现。
     *
     * @param batchId  批次ID
     * @param driverId 指定的司机ID
     * @param vehicleId 车辆ID（可null）
     * @return 更新后的干线配送记录
     */
    OrderDelivery adminAssignTrunkDriver(Long batchId, Long driverId, Long vehicleId);
}
















