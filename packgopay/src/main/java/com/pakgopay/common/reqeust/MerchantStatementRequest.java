package com.pakgopay.common.reqeust;

import lombok.Data;

@Data
public class MerchantStatementRequest {
    private String orderNO;
    private String selectedTransactionType;
    private String transactionStatus;
    private String merchantName;
    private String createStartTime;
    private String createEndTime;

}
