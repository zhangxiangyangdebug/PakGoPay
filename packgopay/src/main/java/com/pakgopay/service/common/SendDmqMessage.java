package com.pakgopay.service.common;

import com.pakgopay.data.entity.Message;
import com.pakgopay.data.entity.TestMessage;
import com.pakgopay.thirdUtil.RabbitmqUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class SendDmqMessage {


    @Autowired
    private RabbitmqUtil rabbitmqUtil;


    public void send(String queueName, TestMessage message) {
        rabbitmqUtil.send(queueName, message);
    }

    public void sendDelay(String queueName, TestMessage message) {
        rabbitmqUtil.sendDelay(queueName, message);
    }

    /**
     * send broad message
     * @param queueName
     * @param message
     */
    public void sendFanout(String queueName, Message message) {
        rabbitmqUtil.sendFanout(queueName, message);
    }

    public void sendToDelayQueue(String message) {
        rabbitmqUtil.sendToDelayQueue(message);
    }
}
