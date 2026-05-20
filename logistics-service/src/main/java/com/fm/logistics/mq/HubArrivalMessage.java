package com.fm.logistics.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Hub 到达消息（#13）接收端 DTO
 * driver-service → logistics-service
 *
 * 干线司机在前端点击"确认到达中转站"后，
 * driver-service 发送此消息，logistics-service 接收并激活末端路线。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class HubArrivalMessage {
    /** 所属批次ID */
    private Long batchId;
    /** 中转站ID */
    private Long hubId;
    /** 干线司机ID */
    private Long trunkDriverId;
    /** 干线配送记录ID */
    private Long trunkDeliveryId;
}
