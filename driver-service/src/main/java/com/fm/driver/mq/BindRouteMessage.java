package com.fm.driver.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 绑定路线消息（#10）
 * driver-service → logistics-service
 * 场景：司机接单后，将 driverId 和 deliveryId 绑定到对应的物流路线
 *
 * 两种模式：
 *   - routeId != null：直接按 routeId 绑定（智能调度模式，司机接近单路线段）
 *   - routeId == null：通过 orderId 查找路线再绑定（传统模式）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BindRouteMessage {
    private Long orderId;
    private Long driverId;
    private Long deliveryId;
    /** 若不为 null，直接按此 routeId 绑定（跳过 orderId 查询） */
    private Long routeId;
}
