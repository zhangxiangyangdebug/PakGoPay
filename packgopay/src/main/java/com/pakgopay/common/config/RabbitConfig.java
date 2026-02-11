package com.pakgopay.common.config;
import com.pakgopay.mq.FrontMessageNotify;
import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.Connection;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Configuration
public class RabbitConfig {
    public static final String DELAY_EXCHANGE = "task.delay.exchange";
    public static final String TASK_COLLECTING_QUEUE = "task.collecting.queue";
    public static final String TASK_PAYING_QUEUE = "task.paying.queue";

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
    public Binding collectingDelayBinding(Queue collectingQueue, CustomExchange delayedExchange) {
        return BindingBuilder.bind(collectingQueue).to(delayedExchange).with(TASK_COLLECTING_QUEUE).noargs();
    }

    @Bean
    public Binding payingDelayBinding(Queue payingQueue, CustomExchange delayedExchange) {
        return BindingBuilder.bind(payingQueue).to(delayedExchange).with(TASK_PAYING_QUEUE).noargs();
    }

    @Bean
    @Scope("prototype")
    public FrontMessageNotify testReceiver() {
        return new FrontMessageNotify();
    }

    // create random queue
    @Bean
    public String mqMsgQueue() throws IOException {
        Connection connection = connectionFactory().createConnection();

        // get an channel
        Channel channel = connection.createChannel(false);

        // bind to exchange
        channel.exchangeDeclare("notice2", BuiltinExchangeType.FANOUT, true);

        // get an random queue from channel
        String queue =  channel.queueDeclare().getQueue();

        // bind random queue to exchange
        channel.queueBind(queue,"notice2","*",null);
        return queue;
    }

    // create listenner to listen queue

    @Bean
    public SimpleMessageListenerContainer simpleMessageListenerContainer(FrontMessageNotify frontMessageNotify) throws IOException {
        SimpleMessageListenerContainer container = new SimpleMessageListenerContainer(connectionFactory());
        container.setQueueNames(mqMsgQueue());
        container.setExposeListenerChannel(true);
        container.setConcurrentConsumers(3); //消费者数量
        //container.setPrefetchCount(10);  //每个消费者能获取的到最大消息数量
        container.setAcknowledgeMode(AcknowledgeMode.MANUAL); //手动确认消费
        container.setMessageListener(frontMessageNotify);//监听处理类
        return container;
    }
}
