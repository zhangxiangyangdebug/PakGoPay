package com.pakgopay.common.entity;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class TransactionInfo {

    // ------------------------ merchant info ---------------------------------
    /**
     * Merchant Id
     */
    private String merchant_id;

    /**
     * Merchant order number (must be unique)
     */
    private String merchantOrderNo;

    // ------------------------ channel info ---------------------------------

    /**
     * Merchant channel code (must be unique)
     */
    private Integer paymentNo;

    // ------------------------ amount info ---------------------------------

    /**
     * Collection amount
     */
    private BigDecimal amount;

    /**
     * Currency code (e.g. VND, PKR, IDR, USD, CNY)
     */
    private String currency;

    // ------------------------ notification info ---------------------------------

    /**
     * Asynchronous notification URL
     */
    private String notificationUrl;

}
