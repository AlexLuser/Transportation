package com.fm.order.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 队列、交换机、绑定配置
 *
 * order-service 角色：
 *   - 生产者：#6 扣减库存（→ shop-service）、#8 创建配送单（→ driver-service）
 *   - 消费者：#9 订单状态更新（← driver-service）、#12 订单重开（← driver-service）
 *
 * 本类仅声明本服务作为【消费者】的队列，以及公共的 Exchange/DLX。
 * 生产者只需知道 Exchange 名称和 Routing Key，无需声明目标队列。
 */
@Configuration
public class RabbitMQConfig {

    // ----------------------------------------------------------------
    //  公共 Exchange 常量（所有服务共享同一套命名）
    // ----------------------------------------------------------------
    public static final String EXCHANGE = "transportation.exchange";
    public static final String DLX      = "transportation.dlx";

    // ----------------------------------------------------------------
    //  #6 库存扣减（order-service → shop-service）
    // ----------------------------------------------------------------
    public static final String ROUTING_STOCK_DEDUCT      = "stock.deduct";

    // ----------------------------------------------------------------
    //  #8a 订单进调度池（order-service → logistics-service）[新增]
    // ----------------------------------------------------------------
    public static final String ROUTING_ADD_TO_POOL       = "dispatch.pool.add";

    // ----------------------------------------------------------------
    //  #8b 创建配送单（order-service → driver-service）[保留，用于直接发货回退场景]
    // ----------------------------------------------------------------
    public static final String ROUTING_DELIVERY_CREATE   = "delivery.create";

    // ----------------------------------------------------------------
    //  #9 订单状态更新（driver-service → order-service）
    // ----------------------------------------------------------------
    public static final String QUEUE_ORDER_STATUS_UPDATE      = "order.status.update.queue";
    public static final String DLQ_ORDER_STATUS_UPDATE        = "order.status.update.dlq";
    public static final String ROUTING_ORDER_STATUS_UPDATE    = "order.status.update";
    public static final String ROUTING_ORDER_STATUS_UPDATE_DL = "order.status.update.dead";

    // ----------------------------------------------------------------
    //  #12 订单重开（driver-service → order-service）
    // ----------------------------------------------------------------
    public static final String QUEUE_ORDER_REOPEN      = "order.reopen.queue";
    public static final String DLQ_ORDER_REOPEN        = "order.reopen.dlq";
    public static final String ROUTING_ORDER_REOPEN    = "order.reopen";
    public static final String ROUTING_ORDER_REOPEN_DL = "order.reopen.dead";

    // ================================================================
    //  Exchange 声明
    // ================================================================

    @Bean
    public DirectExchange transportationExchange() {
        return new DirectExchange(EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(DLX, true, false);
    }

    // ================================================================
    //  #9 队列与绑定
    // ================================================================

    @Bean
    public Queue orderStatusUpdateQueue() {
        return QueueBuilder.durable(QUEUE_ORDER_STATUS_UPDATE)
                .withArgument("x-dead-letter-exchange", DLX)
                .withArgument("x-dead-letter-routing-key", ROUTING_ORDER_STATUS_UPDATE_DL)
                .build();
    }

    @Bean
    public Queue orderStatusUpdateDlq() {
        return QueueBuilder.durable(DLQ_ORDER_STATUS_UPDATE).build();
    }

    @Bean
    public Binding orderStatusUpdateBinding() {
        return BindingBuilder.bind(orderStatusUpdateQueue())
                .to(transportationExchange())
                .with(ROUTING_ORDER_STATUS_UPDATE);
    }

    @Bean
    public Binding orderStatusUpdateDlqBinding() {
        return BindingBuilder.bind(orderStatusUpdateDlq())
                .to(deadLetterExchange())
                .with(ROUTING_ORDER_STATUS_UPDATE_DL);
    }

    // ================================================================
    //  #12 队列与绑定
    // ================================================================

    @Bean
    public Queue orderReopenQueue() {
        return QueueBuilder.durable(QUEUE_ORDER_REOPEN)
                .withArgument("x-dead-letter-exchange", DLX)
                .withArgument("x-dead-letter-routing-key", ROUTING_ORDER_REOPEN_DL)
                .build();
    }

    @Bean
    public Queue orderReopenDlq() {
        return QueueBuilder.durable(DLQ_ORDER_REOPEN).build();
    }

    @Bean
    public Binding orderReopenBinding() {
        return BindingBuilder.bind(orderReopenQueue())
                .to(transportationExchange())
                .with(ROUTING_ORDER_REOPEN);
    }

    @Bean
    public Binding orderReopenDlqBinding() {
        return BindingBuilder.bind(orderReopenDlq())
                .to(deadLetterExchange())
                .with(ROUTING_ORDER_REOPEN_DL);
    }

    // ================================================================
    //  消息转换器（Jackson JSON，跨服务按字段映射，不依赖类名）
    // ================================================================

    @Bean
    public MessageConverter messageConverter() {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();
        converter.setTypePrecedence(Jackson2JavaTypeMapper.TypePrecedence.INFERRED);
        return converter;
    }
}
