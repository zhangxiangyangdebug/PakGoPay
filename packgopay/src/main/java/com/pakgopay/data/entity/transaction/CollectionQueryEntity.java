package com.pakgopay.data.entity.transaction;

import lombok.Data;

import java.util.Map;

@Data
public class CollectionQueryEntity {
    private String transactionNo;
    private String sign;
    private String paymentCheckCollectionUrl;
    private Map<String, Object> channelParams;
}
