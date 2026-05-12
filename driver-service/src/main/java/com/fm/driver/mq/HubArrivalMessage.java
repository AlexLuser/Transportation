package com.fm.driver.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Hub 到达消息（#13）发送端 DTO
 * driver-service → logistics-service
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class HubArrivalMessage {
    private Long batchId;
    private Long hubId;
    private Long trunkDriverId;
    private Long trunkDeliveryId;
}
