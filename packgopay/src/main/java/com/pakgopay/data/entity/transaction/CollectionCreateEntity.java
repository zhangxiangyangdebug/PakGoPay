package com.pakgopay.data.entity.transaction;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

@Data
public class CollectionCreateEntity {
    private String transactionNo;
    private BigDecimal amount;
    private String merchantOrderNo;
    private String merchantUserId;
    private String callbackUrl;
    private String ip;
    private String paymentRequestCollectionUrl;
    private String collectionInterfaceParam;
    private String channelCode;
    private Map<String, Object> channelParams;
}
