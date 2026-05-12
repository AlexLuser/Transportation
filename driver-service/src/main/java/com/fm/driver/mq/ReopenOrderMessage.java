package com.fm.driver.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 订单重开消息（#12）
 * driver-service → order-service
 * 场景：司机取消配送后，将订单从派送中回滚为待揽件
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReopenOrderMessage {
    private Long orderId;
}
