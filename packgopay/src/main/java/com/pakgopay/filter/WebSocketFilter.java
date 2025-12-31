package com.pakgopay.filter;

import com.pakgopay.service.common.AuthorizationService;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;

@Component
public class WebSocketFilter implements ChannelInterceptor {

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String header = accessor.getFirstNativeHeader("Authorization");
            // 校验 token
            String token = header != null && header.startsWith("Bearer ") ? header.substring(7) : null;
            if (token != null && AuthorizationService.verifyToken(token) != null) {
                return message;
            } else {
                return null;
            }
        }
        return message;
    }
}
