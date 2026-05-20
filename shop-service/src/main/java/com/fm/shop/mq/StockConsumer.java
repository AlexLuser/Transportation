package com.fm.shop.mq;

import com.fm.shop.config.RabbitMQConfig;
import com.fm.shop.service.StockService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * shop-service MQ 消费者
 *
 * 消费场景：
 *   #6 库存扣减（order-service 在下单成功后异步发送）
 *
 * 确认机制：AUTO ack + retry(3次) + 失败进死信队列
 * 幂等说明：数据库层采用 UPDATE ... WHERE stock >= quantity 的 CAS 原子操作（warehouseProductMapper.deductStock），
 *           天然防止超扣。若扣减返回 false（库存不足），则进入死信队列等待人工处理。
 */
@Slf4j
@Component
public class StockConsumer {

    @Autowired
    private StockService stockService;

    /**
     * #6 处理库存扣减消息
     * 若扣减失败（极低概率的竞态条件），触发死信队列，可由运营介入补偿（如取消订单/补货）
     */
    @RabbitListener(queues = RabbitMQConfig.QUEUE_STOCK_DEDUCT)
    public void handleStockDeduct(StockDeductMessage message) {
        log.info("[MQ消费] #6 库存扣减: orderId={}, warehouseId={}, productId={}, quantity={}",
                message.getOrderId(), message.getWarehouseId(), message.getProductId(), message.getQuantity());
        try {
            boolean success = stockService.deductStock(
                message.getWarehouseId(),
                message.getProductId(),
                message.getQuantity()
            );
            if (!success) {
                log.error("[MQ消费] #6 库存不足，扣减失败: orderId={}, productId={}, quantity={}",
                        message.getOrderId(), message.getProductId(), message.getQuantity());
                throw new AmqpRejectAndDontRequeueException(
                    "库存不足，扣减失败进入死信队列: orderId=" + message.getOrderId()
                    + ", productId=" + message.getProductId());
            }
            log.info("[MQ消费] #6 库存扣减成功: orderId={}, productId={}", message.getOrderId(), message.getProductId());
        } catch (AmqpRejectAndDontRequeueException e) {
            throw e;
        } catch (Exception e) {
            log.error("[MQ消费] #6 库存扣减异常: orderId={}, error={}", message.getOrderId(), e.getMessage());
            throw new AmqpRejectAndDontRequeueException("库存扣减异常，进入死信队列: " + e.getMessage(), e);
        }
    }
}
