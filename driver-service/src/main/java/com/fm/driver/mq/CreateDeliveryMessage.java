package com.fm.driver.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 创建配送单消息（#8）接收端 DTO
 * order-service → driver-service
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
