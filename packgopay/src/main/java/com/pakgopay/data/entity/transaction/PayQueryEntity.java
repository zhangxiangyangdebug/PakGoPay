package com.pakgopay.data.entity.transaction;

import lombok.Data;

@Data
public class PayQueryEntity {
    private String transactionNo;
    private String paymentCheckPayUrl;
    private String payInterfaceParam;
}
