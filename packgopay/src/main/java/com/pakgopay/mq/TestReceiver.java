package com.pakgopay.mq;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.pakgopay.service.common.NotificationService;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Date;

@Component
public class TestReceiver {

    @Autowired
    private NotificationService notificationService;

    @RabbitListener(queuesToDeclare = @Queue("test"))
    public void reveiveMessage(String message) throws JsonProcessingException {
        //System.out.println("I get a test message: " + new ObjectMapper().readValue(message, TestMessage.class).getContent());
        notificationService.broadcastMessage(message);
        System.out.println(new Date() + "You get it" + message);
    }

    @RabbitListener(queuesToDeclare = @Queue("test-delay-L10S"))
    public void reveiveMessage2(String message) throws JsonProcessingException {
        //System.out.println("I get a test message: " + new ObjectMapper().readValue(message, TestMessage.class).getContent());
        notificationService.broadcastMessage(message);
        System.out.println(new Date() + "You get it" + message);
    }

    @RabbitListener(queuesToDeclare = @Queue("userNotice"))
    public void reveiveNoticeMessage(String message) throws JsonProcessingException {
        //System.out.println("I get a test message: " + new ObjectMapper().readValue(message, TestMessage.class).getContent());
        notificationService.broadcastMessage(message);
        System.out.println(new Date() + "You get it" + message);
    }
}
