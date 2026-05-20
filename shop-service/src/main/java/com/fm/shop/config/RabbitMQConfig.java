package com.fm.shop.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 队列、交换机、绑定配置
 *
 * shop-service 角色：
 *   - 消费者：#6 库存扣减（← order-service）
 *
 * 本类声明本服务作为【消费者】的队列（#6），及公共 Exchange/DLX。
 */
@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE = "transportation.exchange";
    public static final String DLX      = "transportation.dlx";

    // ----------------------------------------------------------------
    //  #6 库存扣减（order-service → shop-service）
    // ----------------------------------------------------------------
    public static final String QUEUE_STOCK_DEDUCT      = "stock.deduct.queue";
    public static final String DLQ_STOCK_DEDUCT        = "stock.deduct.dlq";
    public static final String ROUTING_STOCK_DEDUCT    = "stock.deduct";
    public static final String ROUTING_STOCK_DEDUCT_DL = "stock.deduct.dead";

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
    //  #6 队列与绑定
    // ================================================================

    @Bean
    public Queue stockDeductQueue() {
        return QueueBuilder.durable(QUEUE_STOCK_DEDUCT)
                .withArgument("x-dead-letter-exchange", DLX)
                .withArgument("x-dead-letter-routing-key", ROUTING_STOCK_DEDUCT_DL)
                .build();
    }

    @Bean
    public Queue stockDeductDlq() {
        return QueueBuilder.durable(DLQ_STOCK_DEDUCT).build();
    }

    @Bean
    public Binding stockDeductBinding() {
        return BindingBuilder.bind(stockDeductQueue())
                .to(transportationExchange())
                .with(ROUTING_STOCK_DEDUCT);
    }

    @Bean
    public Binding stockDeductDlqBinding() {
        return BindingBuilder.bind(stockDeductDlq())
                .to(deadLetterExchange())
                .with(ROUTING_STOCK_DEDUCT_DL);
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
