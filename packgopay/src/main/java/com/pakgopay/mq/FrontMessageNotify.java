package com.pakgopay.mq;

import com.google.gson.Gson;
import com.pakgopay.service.common.NotificationService;
import com.rabbitmq.client.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.listener.api.ChannelAwareMessageListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * broadMessage to all consumer and push meessage to websocket
 */
@Component
@Slf4j
public class FrontMessageNotify implements ChannelAwareMessageListener {

    @Autowired
    private NotificationService notificationService;

    @RabbitListener(
        bindings = @QueueBinding(
            value = @Queue(value = "", durable = "false", exclusive = "true", autoDelete = "true"),
            exchange = @Exchange(value = "user-notify", type = "fanout", durable = "true")
        )
    )
    public void onUserNotify(Message message, Channel channel) throws Exception {
        byte[] body = message.getBody();
        String messageStr =  new String(body);
        Gson gson = new Gson();
        com.pakgopay.data.entity.Message localMessage = gson.fromJson(messageStr, com.pakgopay.data.entity.Message.class);
        log.info("get user-notify message:"+new String(body));
        channel.basicAck(message.getMessageProperties().getDeliveryTag(),false);//确认消息消费成功
        notificationService.broadcastMessage(localMessage);
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
