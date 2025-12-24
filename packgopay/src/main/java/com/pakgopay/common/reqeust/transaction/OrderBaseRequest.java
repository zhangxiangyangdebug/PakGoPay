package com.pakgopay.common.reqeust.transaction;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class OrderBaseRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * Merchant order number (must be unique)
     */
    private String merchantOrderNo;

    /**
     * Merchant channel code (must be unique)
     */
    private String channelCode;

    /**
     * Collection amount
     */
    private BigDecimal amount;

    /**
     * Currency code (e.g. VND, PKR, IDR, USD, CNY)
     */
    private String currency;

    /**
     * Asynchronous notification URL
     */
    private String notificationUrl;

    /**
     * Channel-specific parameters
     */
    private Object channelParams;

    /**
     * Remark
     */
    private String remark;
}
