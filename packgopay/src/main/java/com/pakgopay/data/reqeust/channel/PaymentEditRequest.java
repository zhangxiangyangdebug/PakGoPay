package com.pakgopay.data.reqeust.channel;

import com.pakgopay.data.reqeust.BaseRequest;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class PaymentEditRequest extends BaseRequest {
    /**
     * Payment Id
     */
    @NotNull(message = "paymentId is null")
    private String paymentId;

    /**
     * Payment number
     */
    private String paymentNo;

    /**
     * Payment name
     */
    private String paymentName;

    /**
     * Enabled status
     */
    private Integer status;

    /**
     * support type: 0:collect 1:pay 2:collect/pay
     */
    private Integer supportType;

    /** Payment type: 1-app payment, 2-bank card payment */
    private String paymentType;

    /** Bank name */
    private String bankName;

    /** Bank account number */
    private String bankAccount;

    /** Bank account holder name */
    private String bankUserName;

    /** Enabled time period */
    private String enableTimePeriod;

    /** Collection interface parameters */
    private String collectionInterfaceParam;

    /** Payout interface parameters */
    private String payInterfaceParam;

    /** Whether checkout counter is required */
    private Integer isCheckoutCounter;

    /** Collection daily limit */
    private BigDecimal collectionDailyLimit;

    /** Payout daily limit */
    private BigDecimal payDailyLimit;

    /** Collection monthly limit */
    private BigDecimal collectionMonthlyLimit;

    /** Payout monthly limit */
    private BigDecimal payMonthlyLimit;

    /** Payout API request URL */
    private String paymentRequestPayUrl;

    /** Collection API request URL */
    private String paymentRequestCollectionUrl;

    /** Maximum amount */
    private BigDecimal paymentMaxAmount;

    /** Minimum amount */
    private BigDecimal paymentMinAmount;

    /** Payout rate (stored as varchar) */
    private String paymentPayRate;

    /** Collection rate (stored as varchar) */
    private String paymentCollectionRate;

    /** Payout order check URL */
    private String paymentCheckPayUrl;

    /** Collection order check URL */
    private String paymentCheckCollectionUrl;

    /** Whether it is a third-party channel: 0-no, 1-yes (varchar in DB) */
    private String isThird;

    /** Collection callback URL */
    private String collectionCallbackAddr;

    /** Payout callback URL */
    private String payCallbackAddr;

    /** Checkout counter URL */
    private String checkoutCounterUrl;

    /** Currency */
    private String currency;
}
