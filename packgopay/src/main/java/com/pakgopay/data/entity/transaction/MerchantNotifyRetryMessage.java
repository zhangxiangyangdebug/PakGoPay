package com.pakgopay.data.entity.transaction;

import lombok.Data;

import java.io.Serializable;
import java.util.Map;

@Data
public class MerchantNotifyRetryMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    private String transactionNo;
    private String callbackUrl;
    private Map<String, Object> body;
    private Integer attempt;
    private Integer maxAttempts;
}

