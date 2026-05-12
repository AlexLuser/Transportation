package com.fm.driver.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 路线段激活消息（#14）接收端 DTO
 * logistics-service → driver-service
 *
 * 复用于两种场景：
 *   - segmentType=2：末端路线激活（干线到Hub后触发，创建待接单配送记录）
 *   - segmentType=1：干线路线创建（createBatch后立即触发，创建待接单配送记录）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
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
}
