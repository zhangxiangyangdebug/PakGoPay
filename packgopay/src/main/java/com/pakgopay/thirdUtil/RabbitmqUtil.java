package com.pakgopay.thirdUtil;

import com.alibaba.fastjson.JSON;
import com.pakgopay.data.entity.TestMessage;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class RabbitmqUtil {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    public void send(String queueName, TestMessage testMq) {
        rabbitTemplate.convertAndSend(queueName, JSON.toJSONString(testMq));
    }

    public void sendDelay(String queueName, TestMessage testMq) {
        String message = JSON.toJSONString(testMq);
        Map<String, Object> map = new HashMap<>();
        map.put("x-delay", 5000);
        // 延时队列原理：接收消息永远都是某一个topic 。当需要进入延时队列时，通过指定routingKey 将消息放入延时交换机中，延时交换机配置了x-message-delay
        // 可通过头部添加x-delay指定延时时间
        rabbitTemplate.convertAndSend("test-delay-L10S","test-delay-L10S",message, message1 -> {
            message1.getMessageProperties().getHeaders().putAll(map);
            return message1;
        });
    }
}
