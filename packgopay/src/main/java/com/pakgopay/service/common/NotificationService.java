package com.pakgopay.service.common;

import com.alibaba.fastjson2.JSON;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {

    @Autowired
    private SimpMessagingTemplate simpMessagingTemplate;

    /**
     * 广播新订单给订阅的客户端
     * @param message
     */
    public void broadcastMessage(String message) {
        //将消息放到/topic/newOrder
        simpMessagingTemplate.convertAndSend("/topic/newOrder", "您有新消息来啦，内容是：" + JSON.parseObject(message).get("content"));
    }

    /**
     * 死信特定用户
     * @param userId
     * @param message
     */
    public void notifyUser(String userId, String message) {
        simpMessagingTemplate.convertAndSendToUser(userId, "/topic/notification", message);
    }
}
