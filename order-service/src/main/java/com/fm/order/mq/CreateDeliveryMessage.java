package com.fm.order.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 创建配送单消息（#8）
 * order-service → driver-service
 * 场景：商户发货（orderStatus=2）后，异步通知 driver-service 创建配送记录
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateDeliveryMessage {
    private Long orderId;
    private String deliveryAddress;
    private String receiverName;
    private String receiverPhone;
}
