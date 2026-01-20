package com.pakgopay.common.config;
import com.pakgopay.mq.TestReceiver;
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

@Configuration
public class RabbitConfig {

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

    @Bean
    @Scope("prototype")
    public TestReceiver testReceiver() {
        return new TestReceiver();
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
    public SimpleMessageListenerContainer simpleMessageListenerContainer(TestReceiver testReceiver) throws IOException {
        SimpleMessageListenerContainer container = new SimpleMessageListenerContainer(connectionFactory());
        container.setQueueNames(mqMsgQueue());
        container.setExposeListenerChannel(true);
        container.setConcurrentConsumers(3); //消费者数量
        //container.setPrefetchCount(10);  //每个消费者能获取的到最大消息数量
        container.setAcknowledgeMode(AcknowledgeMode.MANUAL); //手动确认消费
        container.setMessageListener(testReceiver);//监听处理类
        return container;
    }
}
