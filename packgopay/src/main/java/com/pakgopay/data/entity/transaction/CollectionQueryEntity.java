package com.pakgopay.data.entity.transaction;

import lombok.Data;

import java.util.Map;

@Data
public class CollectionQueryEntity {
    private String orderNo;
    private String sign;
    private String paymentCheckCollectionUrl;
    private String paymentRequestCollectionQueryUrl;
    private Map<String, Object> channelParams;
}
