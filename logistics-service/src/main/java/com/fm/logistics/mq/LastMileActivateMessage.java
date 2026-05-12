package com.fm.logistics.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 路线段激活消息（#14）发送端 DTO
 * logistics-service → driver-service
 *
 * 复用于两种场景（通过 segmentType 区分）：
 *   segmentType=1：干线路线 — createBatch 后立即发送，通知 driver-service 创建干线待接单配送记录
 *   segmentType=2：末端路线 — 干线到达 Hub 后发送，通知 driver-service 创建末端待接单配送记录
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LastMileActivateMessage {
    /** 路线ID */
    private Long routeId;
    /** 订单ID（干线路线为 null，末端路线必填） */
    private Long orderId;
    /** 所属批次ID */
    private Long batchId;
    /** 中转站ID */
    private Long hubId;
    /** 目的地地址（干线=Hub地址，末端=客户地址） */
    private String endAddress;
    /** 目的地纬度 */
    private Double endLat;
    /** 目的地经度 */
    private Double endLng;
    /** 收货人姓名 */
    private String receiverName;
    /** 收货人电话 */
    private String receiverPhone;
    /** 路线段类型：1=干线 2=末端；默认 2 保持兼容 */
    private Integer segmentType;
}
