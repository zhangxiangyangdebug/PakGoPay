package com.pakgopay.thirdparty.demo;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ThirdPartyDemoRabbitConfig {

    @Bean
    public Queue thirdPartyDemoNotifyQueue(
            @Value("${thirdparty-demo.notify.queue:thirdparty.demo.notify.queue}") String queueName) {
        return new Queue(queueName, true);
    }

    @Bean
    public MessageConverter jacksonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter messageConverter) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(messageConverter);
        return rabbitTemplate;
    }

    @Bean(name = "thirdPartyDemoNotifyListenerContainerFactory")
    public SimpleRabbitListenerContainerFactory thirdPartyDemoNotifyListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter messageConverter,
            @Value("${thirdparty-demo.notify.consumer-concurrency:8}") int concurrentConsumers,
            @Value("${thirdparty-demo.notify.max-consumer-concurrency:16}") int maxConcurrentConsumers,
            @Value("${thirdparty-demo.notify.prefetch:20}") int prefetchCount) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);
        factory.setConcurrentConsumers(concurrentConsumers);
        factory.setMaxConcurrentConsumers(maxConcurrentConsumers);
        factory.setPrefetchCount(prefetchCount);
        factory.setDefaultRequeueRejected(false);
        return factory;
    }
}
