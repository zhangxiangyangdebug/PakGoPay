package com.pakgopay.mapper.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class PaymentDto implements Serializable {
    private static final long serialVersionUID = 1L;

    /** Payment channel ID */
    private Long paymentId;

    /** Payment channel name */
    private String paymentName;

    /** Support type: 0-collection, 1-payout, 2-collection & payout */
    private Integer supportType;

    /** Total order quantity */
    private Long orderQuantity;

    /** Success order quantity */
    private Long successQuantity;

    /** Whether it is a third-party channel: 0-no, 1-yes (varchar in DB) */
    private String isThird;

    /** Payment channel code */
    private String paymentNo;

    /** Payout rate (stored as varchar) */
    private String paymentPayRate;

    /** Collection rate (stored as varchar) */
    private String paymentCollectionRate;

    /** Payout API request URL */
    private String paymentRequestPayUrl;

    /** Collection API request URL */
    private String paymentRequestCollectionUrl;

    /** Payout order check URL */
    private String paymentCheckPayUrl;

    /** Collection order check URL */
    private String paymentCheckCollectionUrl;

    /** Maximum amount */
    private BigDecimal paymentMaxAmount;

    /** Minimum amount */
    private BigDecimal paymentMinAmount;

    /** Collection daily limit */
    private BigDecimal collectionDailyLimit;

    /** Payout daily limit */
    private BigDecimal payDailyLimit;

    /** Collection monthly limit */
    private BigDecimal collectionMonthlyLimit;

    /** Payout monthly limit */
    private BigDecimal payMonthlyLimit;

    /** Enabled time period */
    private String enableTimePeriod;

    /** Collection interface parameters */
    private String collectionInterfaceParam;

    /** Payout interface parameters */
    private String payInterfaceParam;

    /** Collection callback URL */
    private String collectionCallbackAddr;

    /** Payout callback URL */
    private String payCallbackAddr;

    /** Currency */
    private String currency;

    /** Create time (unix timestamp seconds) */
    private Long createTime;

    /** Created by */
    private String createBy;

    /** Update time (unix timestamp seconds) */
    private Long updateTime;

    /** Updated by (note: DB type is datetime, but mapped as unix timestamp here) */
    private String updateBy;

    /** Remark */
    private String remark;

    /** Whether checkout counter is required */
    private Integer isCheckoutCounter;

    /** Checkout counter URL */
    private String checkoutCounterUrl;

    /** Payment type: 1-app payment, 2-bank card payment */
    private String paymentType;

    /** Bank name */
    private String bankName;

    /** Bank account number */
    private String bankAccount;

    /** Bank account holder name */
    private String bankUserName;

    /** Enable status */
    private Integer status;
}
