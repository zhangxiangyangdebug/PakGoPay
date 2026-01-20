package com.pakgopay.service.common;

import com.alibaba.fastjson2.JSON;
import org.json.JSONObject;
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
        JSONObject jsonObject = new JSONObject(message);
        JSONObject messageInfo = new JSONObject(jsonObject.get("content").toString());
        String megInfo = messageInfo.getString("messageInfo");
        String userId = messageInfo.getString("userId");
        simpMessagingTemplate.convertAndSend("/topic/"+userId+"/newOrder", "您有新消息来啦，内容是：" + megInfo);
    }

    /**
     * 死信特定用户
     * @param userId
     * @param message
     */
    public void notifyUser(String userId, String message) {
        simpMessagingTemplate.convertAndSendToUser("/topic/"+userId, "/topic/notification", message);
    }
}
