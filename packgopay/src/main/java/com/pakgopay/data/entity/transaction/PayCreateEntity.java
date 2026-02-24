package com.pakgopay.data.entity.transaction;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

@Data
public class PayCreateEntity {
    private String transactionNo;
    private BigDecimal amount;
    private String currency;
    private String merchantOrderNo;
    private String merchantUserId;
    private String callbackUrl;
    private String bankCode;
    private String accountName;
    private String accountNo;
    private Map<String, Object> channelParams;
    private String ip;
    private String paymentRequestPayUrl;
    private String payInterfaceParam;
}
