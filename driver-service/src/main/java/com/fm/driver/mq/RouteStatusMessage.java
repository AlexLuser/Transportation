package com.fm.driver.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 路线状态更新消息（#11）
 * driver-service → logistics-service
 * 场景：配送状态变更时同步物流路线状态
 *   routeStatus: 1=运输中, 2=已送达, 3=运输异常
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RouteStatusMessage {
    private Long orderId;
    private Integer routeStatus;
}
