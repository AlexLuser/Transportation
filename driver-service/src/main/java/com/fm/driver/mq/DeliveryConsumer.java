package com.fm.driver.mq;

import com.fm.driver.config.RabbitMQConfig;
import com.fm.driver.service.DeliveryService;
import com.fm.driver.service.impl.DeliveryServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * driver-service MQ 消费者
 *
 * 消费场景：
 *   #8  创建配送单（← order-service，商户发货后异步创建配送记录）
 *   #14 末端路线激活（← logistics-service，干线到Hub后激活末端配送单）
 *
 * 确认机制：AUTO ack + retry(3次) + 失败进死信队列
 */
@Slf4j
@Component
public class DeliveryConsumer {

    @Autowired
    private DeliveryService deliveryService;

    @Autowired
    private DeliveryServiceImpl deliveryServiceImpl;

    /**
     * #8 处理创建配送单消息
     */
    @RabbitListener(queues = RabbitMQConfig.QUEUE_DELIVERY_CREATE)
    public void handleCreateDelivery(CreateDeliveryMessage message) {
        log.info("[MQ消费] #8 创建配送单: orderId={}, address={}", message.getOrderId(), message.getDeliveryAddress());
        try {
            deliveryService.createDelivery(
                message.getOrderId(),
                message.getDeliveryAddress(),
                message.getReceiverName(),
                message.getReceiverPhone()
            );
            log.info("[MQ消费] #8 创建配送单成功: orderId={}", message.getOrderId());
        } catch (Exception e) {
            log.error("[MQ消费] #8 创建配送单失败: orderId={}, error={}", message.getOrderId(), e.getMessage());
            throw new AmqpRejectAndDontRequeueException("创建配送单失败，进入死信队列: " + e.getMessage(), e);
        }
    }

    /**
     * #14 处理末端路线激活消息
     * 干线到达 Hub 后，logistics-service 为每个订单发送此消息
     * 此处创建末端配送单，放入待接单大厅
     */
    @RabbitListener(queues = RabbitMQConfig.QUEUE_LAST_MILE_ACTIVATE)
    public void handleLastMileActivate(LastMileActivateMessage message) {
        log.info("[MQ消费] #14 末端配送单创建: orderId={}, batchId={}, hubId={}",
                message.getOrderId(), message.getBatchId(), message.getHubId());
        try {
            deliveryServiceImpl.createLastMileDelivery(message);
            log.info("[MQ消费] #14 末端配送单创建成功: orderId={}", message.getOrderId());
        } catch (Exception e) {
            log.error("[MQ消费] #14 末端配送单创建失败: orderId={}, error={}", message.getOrderId(), e.getMessage());
            throw new AmqpRejectAndDontRequeueException("末端配送单创建失败，进入死信队列: " + e.getMessage(), e);
        }
    }
}
