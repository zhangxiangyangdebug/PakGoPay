package com.pakgopay.data.response.notification;

import com.pakgopay.data.entity.Message;
import lombok.Data;

import java.util.List;

@Data
public class WsMessage {
    private List<Message> messages;

    private Long messageCount;
}
