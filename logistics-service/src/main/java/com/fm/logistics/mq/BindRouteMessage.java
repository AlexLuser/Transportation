package com.fm.logistics.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 绑定路线消息（#10）接收端 DTO
 * driver-service → logistics-service
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BindRouteMessage {
    private Long orderId;
    private Long driverId;
    private Long deliveryId;
    /** 若不为 null，直接按此 routeId 绑定（智能调度司机接单模式） */
    private Long routeId;
}
