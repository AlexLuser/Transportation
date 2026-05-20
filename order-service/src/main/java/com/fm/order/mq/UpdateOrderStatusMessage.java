package com.fm.order.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 更新订单状态消息（#9）接收端 DTO
 * driver-service → order-service
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateOrderStatusMessage {
    private Long orderId;
    private Integer orderStatus;
}
