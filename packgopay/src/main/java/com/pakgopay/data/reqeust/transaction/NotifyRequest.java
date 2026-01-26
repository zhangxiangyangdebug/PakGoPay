package com.pakgopay.data.reqeust.transaction;

import lombok.Data;

@Data
public class NotifyRequest {
    private String transactionNo;
    private String merchantNo;
    private String status;
}
