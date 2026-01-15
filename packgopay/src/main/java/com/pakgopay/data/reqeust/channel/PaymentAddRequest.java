package com.pakgopay.data.reqeust.channel;

import com.pakgopay.data.reqeust.BaseRequest;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class PaymentAddRequest extends BaseRequest {

    // =====================
    // 1) Basic Info
    // =====================

    /**
     * Payment number
     */
    @NotBlank(message = "paymentNo is empty")
    private String paymentNo;

    /**
     * Payment name
     */
    @NotBlank(message = "paymentName is empty")
    private String paymentName;

    /**
     * Currency
     */
    @NotBlank(message = "currency is empty")
    private String currency;

    /**
     * Payment type: 1-app payment, 2-bank card payment
     */
    @NotBlank(message = "paymentType is empty")
    @Pattern(regexp = "[12]", message = "paymentType must be 1 or 2")
    private String paymentType;

    /**
     * Whether it is a third-party channel: 0-no, 1-yes (varchar in DB)
     */
    @NotBlank(message = "isThird is empty")
    @Pattern(regexp = "[01]", message = "paymentType must be 0 or 1")
    private String isThird;


    // =====================
    // 2) Status & Capability
    // =====================

    /**
     * Enabled status
     */
    @NotNull(message = "status is null")
    @Min(value = 0, message = "status must be 0 or 1")
    @Max(value = 1, message = "status must be 0 or 1")
    private Integer status;

    /**
     * Support type: 0-collect, 1-pay, 2-collect/pay
     */
    @NotNull(message = "supportType is null")
    @Min(value = 0, message = "supportType must be 0, 1, 2")
    @Max(value = 2, message = "supportType must be 0, 1, 2")
    private Integer supportType;

    /**
     * Whether checkout counter is required
     */
    @NotNull(message = "isCheckoutCounter is null")
    @Min(value = 0, message = "isCheckoutCounter must be 0 or 1")
    @Max(value = 1, message = "isCheckoutCounter must be 0 or 1")
    private Integer isCheckoutCounter;

    /**
     * Enabled time period
     */
    @NotBlank(message = "enableTimePeriod is empty")
    private String enableTimePeriod;


    // =====================
    // 3) Amount Limits
    // =====================

    /**
     * Maximum amount
     */
    @NotNull(message = "paymentMaxAmount is null")
    private BigDecimal paymentMaxAmount;

    /**
     * Minimum amount
     */
    @NotNull(message = "paymentMaxAmount is null")
    private BigDecimal paymentMinAmount;

    /**
     * Collection daily limit
     */
    private BigDecimal collectionDailyLimit;

    /**
     * Payout daily limit
     */
    private BigDecimal payDailyLimit;

    /**
     * Collection monthly limit
     */
    private BigDecimal collectionMonthlyLimit;

    /**
     * Payout monthly limit
     */
    private BigDecimal payMonthlyLimit;


    // =====================
    // 4) Rate Configuration
    // =====================

    /**
     * Payout rate (stored as varchar)
     */
    private String paymentPayRate;

    /**
     * Collection rate (stored as varchar)
     */
    private String paymentCollectionRate;


    // =====================
    // 5) API Endpoints
    // =====================

    /**
     * Payout API request URL
     */
    private String paymentRequestPayUrl;

    /**
     * Collection API request URL
     */
    private String paymentRequestCollectionUrl;

    /**
     * Payout order check URL
     */
    private String paymentCheckPayUrl;

    /**
     * Collection order check URL
     */
    private String paymentCheckCollectionUrl;


    // =====================
    // 6) Interface Parameters
    // =====================

    /**
     * Collection interface parameters
     */
    private String collectionInterfaceParam;

    /**
     * Payout interface parameters
     */
    private String payInterfaceParam;


    // =====================
    // 7) Callback Configuration
    // =====================

    /**
     * Collection callback URL
     */
    private String collectionCallbackAddr;

    /**
     * Payout callback URL
     */
    private String payCallbackAddr;


    // =====================
    // 8) Checkout Counter
    // =====================

    /**
     * Checkout counter URL
     */
    private String checkoutCounterUrl;


    // =====================
    // 9) Bank Info (for bank channels)
    // =====================

    /**
     * Bank name
     */
    private String bankName;

    /**
     * Bank account number
     */
    private String bankAccount;

    /**
     * Bank account holder name
     */
    private String bankUserName;


    // =====================
    // 10) Meta Info
    // =====================

    /**
     * Remark
     */
    private String remark;

}
