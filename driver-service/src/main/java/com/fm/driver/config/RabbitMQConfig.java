package com.fm.driver.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 队列、交换机、绑定配置
 *
 * driver-service 角色：
 *   - 生产者：#9 更新订单状态（→ order-service）
 *             #10 绑定路线（→ logistics-service）
 *             #11 更新路线状态（→ logistics-service）
 *             #12 订单重开（→ order-service）
 *   - 消费者：#8 创建配送单（← order-service）
 *
 * 本类声明本服务作为【消费者】的队列（#8），及公共 Exchange/DLX。
 */
@Configuration
public class RabbitMQConfig {

    // ----------------------------------------------------------------
    //  公共 Exchange 常量
    // ----------------------------------------------------------------
    public static final String EXCHANGE = "transportation.exchange";
    public static final String DLX      = "transportation.dlx";

    // ----------------------------------------------------------------
    //  #9 更新订单状态（driver-service → order-service）Routing Key
    // ----------------------------------------------------------------
    public static final String ROUTING_ORDER_STATUS_UPDATE = "order.status.update";

    // ----------------------------------------------------------------
    //  #10 绑定路线（driver-service → logistics-service）Routing Key
    // ----------------------------------------------------------------
    public static final String ROUTING_ROUTE_BIND = "route.bind";

    // ----------------------------------------------------------------
    //  #11 更新路线状态（driver-service → logistics-service）Routing Key
    // ----------------------------------------------------------------
    public static final String ROUTING_ROUTE_STATUS_UPDATE = "route.status.update";

    // ----------------------------------------------------------------
    //  #12 订单重开（driver-service → order-service）Routing Key
    // ----------------------------------------------------------------
    public static final String ROUTING_ORDER_REOPEN = "order.reopen";

    // ----------------------------------------------------------------
    //  #8 创建配送单（order-service → driver-service）消费者队列
    // ----------------------------------------------------------------
    public static final String QUEUE_DELIVERY_CREATE      = "delivery.create.queue";
    public static final String DLQ_DELIVERY_CREATE        = "delivery.create.dlq";
    public static final String ROUTING_DELIVERY_CREATE    = "delivery.create";
    public static final String ROUTING_DELIVERY_CREATE_DL = "delivery.create.dead";

    // ----------------------------------------------------------------
    //  #13 Hub到达通知（driver-service → logistics-service）Routing Key
    // ----------------------------------------------------------------
    public static final String ROUTING_HUB_ARRIVAL = "hub.arrival";

    // ----------------------------------------------------------------
    //  #14 末端路线激活（logistics-service → driver-service）消费者队列
    // ----------------------------------------------------------------
    public static final String QUEUE_LAST_MILE_ACTIVATE      = "last.mile.activate.queue";
    public static final String DLQ_LAST_MILE_ACTIVATE        = "last.mile.activate.dlq";
    public static final String ROUTING_LAST_MILE_ACTIVATE    = "last.mile.activate";
    public static final String ROUTING_LAST_MILE_ACTIVATE_DL = "last.mile.activate.dead";

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
    //  #8 队列与绑定
    // ================================================================

    @Bean
    public Queue deliveryCreateQueue() {
        return QueueBuilder.durable(QUEUE_DELIVERY_CREATE)
                .withArgument("x-dead-letter-exchange", DLX)
                .withArgument("x-dead-letter-routing-key", ROUTING_DELIVERY_CREATE_DL)
                .build();
    }

    @Bean
    public Queue deliveryCreateDlq() {
        return QueueBuilder.durable(DLQ_DELIVERY_CREATE).build();
    }

    @Bean
    public Binding deliveryCreateBinding() {
        return BindingBuilder.bind(deliveryCreateQueue())
                .to(transportationExchange())
                .with(ROUTING_DELIVERY_CREATE);
    }

    @Bean
    public Binding deliveryCreateDlqBinding() {
        return BindingBuilder.bind(deliveryCreateDlq())
                .to(deadLetterExchange())
                .with(ROUTING_DELIVERY_CREATE_DL);
    }

    // ================================================================
    //  #14 末端路线激活队列与绑定
    // ================================================================

    @Bean
    public Queue lastMileActivateQueue() {
        return QueueBuilder.durable(QUEUE_LAST_MILE_ACTIVATE)
                .withArgument("x-dead-letter-exchange", DLX)
                .withArgument("x-dead-letter-routing-key", ROUTING_LAST_MILE_ACTIVATE_DL)
                .build();
    }

    @Bean
    public Queue lastMileActivateDlq() {
        return QueueBuilder.durable(DLQ_LAST_MILE_ACTIVATE).build();
    }

    @Bean
    public Binding lastMileActivateBinding() {
        return BindingBuilder.bind(lastMileActivateQueue())
                .to(transportationExchange())
                .with(ROUTING_LAST_MILE_ACTIVATE);
    }

    @Bean
    public Binding lastMileActivateDlqBinding() {
        return BindingBuilder.bind(lastMileActivateDlq())
                .to(deadLetterExchange())
                .with(ROUTING_LAST_MILE_ACTIVATE_DL);
    }

    // ================================================================
    //  消息转换器
    // ================================================================

    @Bean
    public MessageConverter messageConverter() {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();
        converter.setTypePrecedence(Jackson2JavaTypeMapper.TypePrecedence.INFERRED);
        return converter;
    }
}
