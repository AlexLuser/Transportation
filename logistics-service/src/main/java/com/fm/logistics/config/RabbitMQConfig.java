package com.fm.logistics.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 队列、交换机、绑定配置
 *
 * logistics-service 角色：
 *   - 消费者：#10 绑定路线（← driver-service）
 *             #11 更新路线状态（← driver-service）
 *             #13 Hub到达通知（← driver-service）
 *   - 生产者：#14 末端路线激活（→ driver-service）
 *
 * 本类声明本服务作为【消费者】的队列，及公共 Exchange/DLX。
 */
@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE = "transportation.exchange";
    public static final String DLX      = "transportation.dlx";

    // ----------------------------------------------------------------
    //  #10 绑定路线（driver-service → logistics-service）
    // ----------------------------------------------------------------
    public static final String QUEUE_ROUTE_BIND      = "route.bind.queue";
    public static final String DLQ_ROUTE_BIND        = "route.bind.dlq";
    public static final String ROUTING_ROUTE_BIND    = "route.bind";
    public static final String ROUTING_ROUTE_BIND_DL = "route.bind.dead";

    // ----------------------------------------------------------------
    //  #11 更新路线状态（driver-service → logistics-service）
    // ----------------------------------------------------------------
    public static final String QUEUE_ROUTE_STATUS_UPDATE      = "route.status.update.queue";
    public static final String DLQ_ROUTE_STATUS_UPDATE        = "route.status.update.dlq";
    public static final String ROUTING_ROUTE_STATUS_UPDATE    = "route.status.update";
    public static final String ROUTING_ROUTE_STATUS_UPDATE_DL = "route.status.update.dead";

    // ----------------------------------------------------------------
    //  #13 Hub到达通知（driver-service → logistics-service）
    // ----------------------------------------------------------------
    public static final String QUEUE_HUB_ARRIVAL      = "hub.arrival.queue";
    public static final String DLQ_HUB_ARRIVAL        = "hub.arrival.dlq";
    public static final String ROUTING_HUB_ARRIVAL    = "hub.arrival";
    public static final String ROUTING_HUB_ARRIVAL_DL = "hub.arrival.dead";

    // ----------------------------------------------------------------
    //  #14 末端路线激活（logistics-service → driver-service）Routing Key
    // ----------------------------------------------------------------
    public static final String ROUTING_LAST_MILE_ACTIVATE = "last.mile.activate";

    // ----------------------------------------------------------------
    //  #15 订单入调度池（order-service → logistics-service）[新增]
    // ----------------------------------------------------------------
    public static final String QUEUE_DISPATCH_POOL_ADD      = "dispatch.pool.add.queue";
    public static final String DLQ_DISPATCH_POOL_ADD        = "dispatch.pool.add.dlq";
    public static final String ROUTING_DISPATCH_POOL_ADD    = "dispatch.pool.add";
    public static final String ROUTING_DISPATCH_POOL_ADD_DL = "dispatch.pool.add.dead";

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
    //  #10 队列与绑定
    // ================================================================

    @Bean
    public Queue routeBindQueue() {
        return QueueBuilder.durable(QUEUE_ROUTE_BIND)
                .withArgument("x-dead-letter-exchange", DLX)
                .withArgument("x-dead-letter-routing-key", ROUTING_ROUTE_BIND_DL)
                .build();
    }

    @Bean
    public Queue routeBindDlq() {
        return QueueBuilder.durable(DLQ_ROUTE_BIND).build();
    }

    @Bean
    public Binding routeBindBinding() {
        return BindingBuilder.bind(routeBindQueue())
                .to(transportationExchange())
                .with(ROUTING_ROUTE_BIND);
    }

    @Bean
    public Binding routeBindDlqBinding() {
        return BindingBuilder.bind(routeBindDlq())
                .to(deadLetterExchange())
                .with(ROUTING_ROUTE_BIND_DL);
    }

    // ================================================================
    //  #11 队列与绑定
    // ================================================================

    @Bean
    public Queue routeStatusUpdateQueue() {
        return QueueBuilder.durable(QUEUE_ROUTE_STATUS_UPDATE)
                .withArgument("x-dead-letter-exchange", DLX)
                .withArgument("x-dead-letter-routing-key", ROUTING_ROUTE_STATUS_UPDATE_DL)
                .build();
    }

    @Bean
    public Queue routeStatusUpdateDlq() {
        return QueueBuilder.durable(DLQ_ROUTE_STATUS_UPDATE).build();
    }

    @Bean
    public Binding routeStatusUpdateBinding() {
        return BindingBuilder.bind(routeStatusUpdateQueue())
                .to(transportationExchange())
                .with(ROUTING_ROUTE_STATUS_UPDATE);
    }

    @Bean
    public Binding routeStatusUpdateDlqBinding() {
        return BindingBuilder.bind(routeStatusUpdateDlq())
                .to(deadLetterExchange())
                .with(ROUTING_ROUTE_STATUS_UPDATE_DL);
    }

    // ================================================================
    //  #13 Hub 到达队列与绑定
    // ================================================================

    @Bean
    public Queue hubArrivalQueue() {
        return QueueBuilder.durable(QUEUE_HUB_ARRIVAL)
                .withArgument("x-dead-letter-exchange", DLX)
                .withArgument("x-dead-letter-routing-key", ROUTING_HUB_ARRIVAL_DL)
                .build();
    }

    @Bean
    public Queue hubArrivalDlq() {
        return QueueBuilder.durable(DLQ_HUB_ARRIVAL).build();
    }

    @Bean
    public Binding hubArrivalBinding() {
        return BindingBuilder.bind(hubArrivalQueue())
                .to(transportationExchange())
                .with(ROUTING_HUB_ARRIVAL);
    }

    @Bean
    public Binding hubArrivalDlqBinding() {
        return BindingBuilder.bind(hubArrivalDlq())
                .to(deadLetterExchange())
                .with(ROUTING_HUB_ARRIVAL_DL);
    }

    // ================================================================
    //  #15 调度池队列与绑定
    // ================================================================

    @Bean
    public Queue dispatchPoolAddQueue() {
        return QueueBuilder.durable(QUEUE_DISPATCH_POOL_ADD)
                .withArgument("x-dead-letter-exchange", DLX)
                .withArgument("x-dead-letter-routing-key", ROUTING_DISPATCH_POOL_ADD_DL)
                .build();
    }

    @Bean
    public Queue dispatchPoolAddDlq() {
        return QueueBuilder.durable(DLQ_DISPATCH_POOL_ADD).build();
    }

    @Bean
    public Binding dispatchPoolAddBinding() {
        return BindingBuilder.bind(dispatchPoolAddQueue())
                .to(transportationExchange())
                .with(ROUTING_DISPATCH_POOL_ADD);
    }

    @Bean
    public Binding dispatchPoolAddDlqBinding() {
        return BindingBuilder.bind(dispatchPoolAddDlq())
                .to(deadLetterExchange())
                .with(ROUTING_DISPATCH_POOL_ADD_DL);
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
