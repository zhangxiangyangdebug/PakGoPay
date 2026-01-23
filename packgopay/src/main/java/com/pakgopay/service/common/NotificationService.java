package com.pakgopay.service.common;

import com.alibaba.fastjson2.JSON;
import com.pakgopay.common.constant.CommonConstant;
import com.pakgopay.common.enums.ResultCode;
import com.pakgopay.common.exception.PakGoPayException;
import com.pakgopay.data.entity.Message;
import com.pakgopay.data.response.CommonResponse;
import com.pakgopay.data.response.notification.WsMessage;
import com.pakgopay.thirdUtil.GoogleUtil;
import com.pakgopay.thirdUtil.RedisUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.pakgopay.common.constant.CommonConstant.BODY_KEY_PREFIX;
import static com.pakgopay.common.constant.CommonConstant.USER_ZSET_PREFIX;

@Service
public class NotificationService {

    @Autowired
    private SimpMessagingTemplate simpMessagingTemplate;
    @Autowired
    private RedisUtil redisUtil;

    /**
     * 广播新订单给订阅的客户端
     * @param message
     */
    public void broadcastMessage(Message message) {
        //将消息放到/topic/newOrder
        //JSONObject jsonObject = new JSONObject(message);
        //JSONObject messageInfo = new JSONObject(jsonObject.get("content").toString());
        //String megInfo = messageInfo.getString("messageInfo");
        String userId = message.getUserId();
        simpMessagingTemplate.convertAndSend("/topic/"+userId+"/newOrder", message);
    }

    /**
     * 死信特定用户
     * @param userId
     * @param message
     */
    public void notifyUser(String userId, String message) {
        simpMessagingTemplate.convertAndSendToUser("/topic/"+userId, "/topic/notification", message);
    }

    public CommonResponse getUserNofityMessage(HttpServletRequest request) {
        String userId = getUserIdFromToken(request);
        List<Message> messages = redisUtil.getMessages(userId);
        Long messageCount = countUnread(userId);
        WsMessage wsMessage = new WsMessage();
        wsMessage.setMessageCount(messageCount);
        wsMessage.setMessages(messages);
        return CommonResponse.success(wsMessage);
    }

    public CommonResponse markRead(HttpServletRequest request, String msgId) {
        String userId = getUserIdFromToken(request);
        String userKey = USER_ZSET_PREFIX + userId;
        String bodyKey = BODY_KEY_PREFIX + msgId;
        redisUtil.removeMessages(userKey, bodyKey);
        Long messageCount = countUnread(userId);
        List<Message> messages = redisUtil.getMessages(userId);
        WsMessage wsMessage = new WsMessage();
        wsMessage.setMessageCount(messageCount);
        wsMessage.setMessages(messages);
        return CommonResponse.success(wsMessage);
    }

    private long countUnread(String userId) {
        String userKey = USER_ZSET_PREFIX + userId;
        Long count = redisUtil.noReadMessageCount(userKey);
        return count;
    }

    private String getUserIdFromToken(HttpServletRequest request) {
        String userInfo = GoogleUtil.getUserInfoFromToken(request);
        if(userInfo==null){
            throw new PakGoPayException(ResultCode.TOKEN_IS_EXPIRE);
        }
        String userId = userInfo.split("&")[0];
        return userId;
    }
}
