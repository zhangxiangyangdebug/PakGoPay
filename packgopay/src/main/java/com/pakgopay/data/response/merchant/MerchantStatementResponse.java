package com.pakgopay.data.response.merchant;

import lombok.Data;

@Data
public class MerchantStatementResponse {
    private String orderNO;
    private String merchantName;
    private String transactionType;
    private String transactionStatus;
    private String transactionCurrencyType;
    private String transactionCashAmount;
    private String transactionCommission;
    private String beforeTransactionAccountBalance;
    private String afterTransactionAccountBalance;
    private String transactionTime;
    private String transactionReason;
    private String operator;
}
