package com.pakgopay.mq;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.gson.Gson;
import com.pakgopay.common.config.RabbitConfig;
import com.pakgopay.service.common.NotificationService;
import com.pakgopay.thirdUtil.RabbitmqUtil;
import com.rabbitmq.client.*;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.annotation.Before;
import org.json.JSONObject;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.connection.Connection;
import org.springframework.amqp.rabbit.listener.api.ChannelAwareMessageListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Date;
import java.util.concurrent.TimeoutException;

@Component
@Slf4j
public class TestReceiver implements ChannelAwareMessageListener {

    @Autowired
    private NotificationService notificationService;
    @Autowired
    private RabbitmqUtil rabbitmqUtil;

    @Autowired
    private RabbitConfig rabbitConfig;

    @RabbitListener(queuesToDeclare = @Queue("test"))
    public void reveiveMessage(String message) throws JsonProcessingException {
        //System.out.println("I get a test message: " + new ObjectMapper().readValue(message, TestMessage.class).getContent());
        //notificationService.broadcastMessage(message);
        System.out.println(new Date() + "You get it" + message);
    }

    @RabbitListener(queuesToDeclare = @Queue("test-delay-L10S"))
    public void reveiveMessage2(String message) throws JsonProcessingException {
        //System.out.println("I get a test message: " + new ObjectMapper().readValue(message, TestMessage.class).getContent());
        //notificationService.broadcastMessage(message);
        System.out.println(new Date() + "You get it" + message);
    }

    @Override
    public void onMessage(Message message, Channel channel) throws Exception {
        byte[] body = message.getBody();
        String messageStr =  new String(body);
        Gson gson = new Gson();
        com.pakgopay.data.entity.Message localMessage = gson.fromJson(messageStr, com.pakgopay.data.entity.Message.class);
        log.info("接收到广播消息:"+new String(body));
        channel.basicAck(message.getMessageProperties().getDeliveryTag(),false);//确认消息消费成功
        notificationService.broadcastMessage(localMessage);
        /*System.out.println(new Date() + "You get it" + messageStr);*/
    }
}
