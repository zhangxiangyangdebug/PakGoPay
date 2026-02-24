package com.pakgopay.data.entity.transaction;

import lombok.Data;

import java.util.Map;

@Data
public class PayQueryEntity {
    private String transactionNo;
    private String sign;
    private String paymentCheckPayUrl;
    private Map<String, Object> channelParams;
}
