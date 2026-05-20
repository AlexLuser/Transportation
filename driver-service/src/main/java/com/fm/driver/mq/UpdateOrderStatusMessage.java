package com.fm.driver.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 更新订单状态消息（#9）
 * driver-service → order-service
 * 场景：接单后设为派送中(3)、送达后设为已完成(4)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateOrderStatusMessage {
    private Long orderId;
    private Integer orderStatus;
}
