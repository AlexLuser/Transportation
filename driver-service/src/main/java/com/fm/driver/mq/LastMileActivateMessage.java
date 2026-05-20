package com.fm.driver.mq;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 路线段激活消息（#14）接收端 DTO
 * logistics-service → driver-service
 *
 * 复用于两种场景：
 *   - segmentType=1：干线路线创建（createBatch 后立即触发，创建干线配送记录）
 *   - segmentType=2：末端路线激活（干线到 Hub 后触发，创建末端配送记录）
 */
@Data
@NoArgsConstructor
public class LastMileActivateMessage {
    private Long routeId;
    private Long orderId;
    private Long batchId;
    private Long hubId;
    private String endAddress;
    private Double endLat;
    private Double endLng;
    private String receiverName;
    private String receiverPhone;
    /** 路线段类型：1=干线 2=末端；默认 2 保持兼容 */
    private Integer segmentType;

    /**
     * 管理员预分配的司机ID（非空时直接建已接单(1)记录，跳过待接单大厅）
     * null 表示走原有待接单流程
     */
    private Long preAssignedDriverId;
}
