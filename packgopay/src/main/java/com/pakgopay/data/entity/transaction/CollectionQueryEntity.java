package com.pakgopay.data.entity.transaction;

import lombok.Data;

@Data
public class CollectionQueryEntity {
    private String transactionNo;
    private String paymentCheckCollectionUrl;
    private String collectionInterfaceParam;
}
