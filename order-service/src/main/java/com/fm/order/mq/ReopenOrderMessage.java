package com.fm.order.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 订单重开消息（#12）接收端 DTO
 * driver-service → order-service
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReopenOrderMessage {
    private Long orderId;
}
