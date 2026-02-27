package com.pakgopay.data.reqeust.systemConfig;

import lombok.Data;

@Data
public class TelegramConfigRequest {
    private String token;
    private String chatId;
    private String webhookSecret;
    private String allowedUserIds;
    private Integer enabled;
    private Integer googleCode;
}
