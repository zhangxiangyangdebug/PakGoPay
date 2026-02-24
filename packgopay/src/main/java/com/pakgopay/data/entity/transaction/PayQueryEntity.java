package com.pakgopay.data.entity.transaction;

import lombok.Data;

import java.util.Map;

@Data
public class PayQueryEntity {
    private String orderNo;
    private String sign;
    private String paymentCheckPayUrl;
    private String paymentRequestPayQueryUrl;
    private Map<String, Object> channelParams;
}
