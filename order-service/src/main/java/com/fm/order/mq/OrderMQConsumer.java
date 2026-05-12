package com.fm.order.mq;

import com.fm.order.config.RabbitMQConfig;
import com.fm.order.service.OrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * order-service MQ 消费者
 *
 * 消费场景：
 *   #9  订单状态更新（driver-service 发送：接单→派送中、送达→已完成）
 *   #12 订单重开（driver-service 发送：司机取消配送→订单回到待揽件）
 *
 * 确认机制：Spring AMQP AUTO ack + retry（3次）+ 失败进死信队列（DLQ）
 */
@Slf4j
@Component
public class OrderMQConsumer {

    @Autowired
    private OrderService orderService;

    /**
     * #9 处理订单状态更新消息
     * 消息由 driver-service 在接单或送达时发送
     */
    @RabbitListener(queues = RabbitMQConfig.QUEUE_ORDER_STATUS_UPDATE)
    public void handleOrderStatusUpdate(UpdateOrderStatusMessage message) {
        log.info("[MQ消费] #9 订单状态更新: orderId={}, status={}", message.getOrderId(), message.getOrderStatus());
        try {
            orderService.updateOrderStatus(message.getOrderId(), message.getOrderStatus());
            log.info("[MQ消费] #9 订单状态更新成功: orderId={}", message.getOrderId());
        } catch (Exception e) {
            log.error("[MQ消费] #9 订单状态更新失败: orderId={}, error={}", message.getOrderId(), e.getMessage());
            throw new AmqpRejectAndDontRequeueException("订单状态更新失败，进入死信队列: " + e.getMessage(), e);
        }
    }

    /**
     * #12 处理订单重开消息
     * 消息由 driver-service 在司机取消配送时发送，将订单从派送中回滚为待揽件
     */
    @RabbitListener(queues = RabbitMQConfig.QUEUE_ORDER_REOPEN)
    public void handleOrderReopen(ReopenOrderMessage message) {
        log.info("[MQ消费] #12 订单重开: orderId={}", message.getOrderId());
        try {
            orderService.reopenOrderToPendingPickup(message.getOrderId());
            log.info("[MQ消费] #12 订单重开成功: orderId={}", message.getOrderId());
        } catch (Exception e) {
            log.error("[MQ消费] #12 订单重开失败: orderId={}, error={}", message.getOrderId(), e.getMessage());
            throw new AmqpRejectAndDontRequeueException("订单重开失败，进入死信队列: " + e.getMessage(), e);
        }
    }
}
