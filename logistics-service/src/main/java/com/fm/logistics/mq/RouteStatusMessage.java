package com.fm.logistics.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 路线状态更新消息（#11）接收端 DTO
 * driver-service → logistics-service
 *   routeStatus: 1=运输中, 2=已送达, 3=运输异常
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RouteStatusMessage {
    private Long orderId;
    private Integer routeStatus;
}
