package com.pakgopay.common.config;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class RabbitConfig {
    public static final String DELAY_EXCHANGE = "task.delay.exchange";
    public static final String TASK_COLLECTING_QUEUE = "task.collecting.queue";
    public static final String TASK_PAYING_QUEUE = "task.paying.queue";
    public static final String MERCHANT_NOTIFY_COLLECTION_QUEUE = "merchant.notify.collection.queue";
    public static final String MERCHANT_NOTIFY_PAYING_QUEUE = "merchant.notify.paying.queue";
    public static final String ORDER_TIMEOUT_COLLECTION_QUEUE = "order.timeout.collection.queue";
    public static final String ORDER_TIMEOUT_PAYING_QUEUE = "order.timeout.paying.queue";

    @Value("${spring.rabbitmq.host}")
    private String rabbitHost;
    @Value("${spring.rabbitmq.username}")
    private String rabbitUser;
    @Value("${spring.rabbitmq.password}")
    private String rabbitPassword;

    @Value("${spring.rabbitmq.port}")
    private Integer port;

    @Bean
    public ConnectionFactory connectionFactory() {
        CachingConnectionFactory factory = new CachingConnectionFactory("localhost");
        factory.setHost(rabbitHost);
        factory.setPort(port);
        factory.setUsername(rabbitUser);
        factory.setPassword(rabbitPassword);
        return factory;
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        return new RabbitTemplate(connectionFactory);
    }

    /**
     * Use MANUAL ack for @RabbitListener methods because listeners call channel.basicAck/basicNack.
     */
    @Bean(name = "rabbitListenerContainerFactory")
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        return factory;
    }

    /**
     * Dedicated listener factory for order-timeout queues.
     * Use AUTO ack to prevent repeated delivery when handler returns normally.
     */
    @Bean(name = "orderTimeoutRabbitListenerContainerFactory")
    public SimpleRabbitListenerContainerFactory orderTimeoutRabbitListenerContainerFactory(
            ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setAcknowledgeMode(AcknowledgeMode.AUTO);
        return factory;
    }

    // delayed exchange/queue for retry with x-delay
    @Bean
    public CustomExchange delayedExchange() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-delayed-type", "direct");
        return new CustomExchange(DELAY_EXCHANGE, "x-delayed-message", true, false, args);
    }

    @Bean
    public Queue collectingQueue() {
        return new Queue(TASK_COLLECTING_QUEUE, true);
    }

    @Bean
    public Queue payingQueue() {
        return new Queue(TASK_PAYING_QUEUE, true);
    }

    @Bean
    public Queue orderTimeoutCollectionQueue() {
        return new Queue(ORDER_TIMEOUT_COLLECTION_QUEUE, true);
    }

    @Bean
    public Queue orderTimeoutPayingQueue() {
        return new Queue(ORDER_TIMEOUT_PAYING_QUEUE, true);
    }

    @Bean
    public Queue merchantNotifyCollectionQueue() {
        return new Queue(MERCHANT_NOTIFY_COLLECTION_QUEUE, true);
    }

    @Bean
    public Queue merchantNotifyPayingQueue() {
        return new Queue(MERCHANT_NOTIFY_PAYING_QUEUE, true);
    }

    @Bean
    public Binding collectingDelayBinding(Queue collectingQueue, CustomExchange delayedExchange) {
        return BindingBuilder.bind(collectingQueue).to(delayedExchange).with(TASK_COLLECTING_QUEUE).noargs();
    }

    @Bean
    public Binding payingDelayBinding(Queue payingQueue, CustomExchange delayedExchange) {
        return BindingBuilder.bind(payingQueue).to(delayedExchange).with(TASK_PAYING_QUEUE).noargs();
    }

    @Bean
    public Binding orderTimeoutCollectionDelayBinding(
            Queue orderTimeoutCollectionQueue,
            CustomExchange delayedExchange) {
        return BindingBuilder.bind(orderTimeoutCollectionQueue)
                .to(delayedExchange)
                .with(ORDER_TIMEOUT_COLLECTION_QUEUE)
                .noargs();
    }

    @Bean
    public Binding orderTimeoutPayingDelayBinding(
            Queue orderTimeoutPayingQueue,
            CustomExchange delayedExchange) {
        return BindingBuilder.bind(orderTimeoutPayingQueue)
                .to(delayedExchange)
                .with(ORDER_TIMEOUT_PAYING_QUEUE)
                .noargs();
    }

    @Bean
    public Binding merchantNotifyCollectionDelayBinding(
            Queue merchantNotifyCollectionQueue,
            CustomExchange delayedExchange) {
        return BindingBuilder.bind(merchantNotifyCollectionQueue)
                .to(delayedExchange)
                .with(MERCHANT_NOTIFY_COLLECTION_QUEUE)
                .noargs();
    }

    @Bean
    public Binding merchantNotifyPayingDelayBinding(
            Queue merchantNotifyPayingQueue,
            CustomExchange delayedExchange) {
        return BindingBuilder.bind(merchantNotifyPayingQueue)
                .to(delayedExchange)
                .with(MERCHANT_NOTIFY_PAYING_QUEUE)
                .noargs();
    }

}
