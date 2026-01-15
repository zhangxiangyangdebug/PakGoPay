package com.pakgopay.data.reqeust.merchant;

import com.pakgopay.data.reqeust.BaseRequest;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class MerchantAddRequest extends BaseRequest {

    // =====================
    // 1) Identity & Ownership
    // =====================

    /** Parent ID (upper-level agent ID) */
    private String parentId;

    /** Merchant name */
    @NotBlank(message = "merchantName is empty")
    private String merchantName;

    /** Account login name */
    @NotBlank(message = "accountName is empty")
    private String accountName;

    /** Account password */
    @NotBlank(message = "accountPwd is empty")
    @Size(min = 6, max = 32, message = "accountPwd length must be 6-32")
    private String accountPwd;

    /** Confirm password */
    @NotBlank(message = "accountConfirmPwd is empty")
    @Size(min = 6, max = 32, message = "accountConfirmPwd length must be 6-32")
    private String accountConfirmPwd;


    // =====================
    // 2) Basic Status & Capability
    // =====================

    /** Support type: 0=collect, 1=pay, 2=collect/pay */
    @NotNull(message = "supportType is null")
    @Min(value = 0, message = "supportType must be 0, 1 or 2")
    @Max(value = 2, message = "supportType must be 0, 1 or 2")
    private Integer supportType;

    /** Status (0 = Disabled, 1 = Enabled) */
    @NotNull(message = "status is null")
    @Min(value = 0, message = "status must be 0 or 1")
    @Max(value = 1, message = "status must be 0 or 1")
    private Integer status;


    // =====================
    // 3) Risk & Notification
    // =====================

    /** Risk level */
    @NotNull(message = "riskLevel is null")
    @Min(value = 0, message = "riskLevel must be >= 0")
    private Integer riskLevel;

    /** Whether notification is enabled (0 = No, 1 = Yes) */
    @NotNull(message = "notificationEnable is null")
    @Min(value = 0, message = "notificationEnable must be 0 or 1")
    @Max(value = 1, message = "notificationEnable must be 0 or 1")
    private Integer notificationEnable;


    // =====================
    // 4) Collection Fee Configuration
    // =====================

    /** Collection rate */
    @NotNull(message = "collectionRate is null")
    @DecimalMin(value = "0", inclusive = true, message = "collectionRate must be >= 0")
    private BigDecimal collectionRate;

    /** Collection fixed fee */
    @NotNull(message = "collectionFixedFee is null")
    @DecimalMin(value = "0", inclusive = true, message = "collectionFixedFee must be >= 0")
    private BigDecimal collectionFixedFee;

    /** Collection maximum fee */
    @NotNull(message = "collectionMaxFee is null")
    @DecimalMin(value = "0", inclusive = true, message = "collectionMaxFee must be >= 0")
    private BigDecimal collectionMaxFee;

    /** Collection minimum fee */
    @NotNull(message = "collectionMinFee is null")
    @DecimalMin(value = "0", inclusive = true, message = "collectionMinFee must be >= 0")
    private BigDecimal collectionMinFee;


    // =====================
    // 5) Payout Fee Configuration
    // =====================

    /** Payout rate */
    @NotNull(message = "payRate is null")
    @DecimalMin(value = "0", inclusive = true, message = "payRate must be >= 0")
    private BigDecimal payRate;

    /** Payout fixed fee */
    @NotNull(message = "payFixedFee is null")
    @DecimalMin(value = "0", inclusive = true, message = "payFixedFee must be >= 0")
    private BigDecimal payFixedFee;

    /** Payout maximum fee */
    @NotNull(message = "payMaxFee is null")
    @DecimalMin(value = "0", inclusive = true, message = "payMaxFee must be >= 0")
    private BigDecimal payMaxFee;

    /** Payout minimum fee */
    @NotNull(message = "payMinFee is null")
    @DecimalMin(value = "0", inclusive = true, message = "payMinFee must be >= 0")
    private BigDecimal payMinFee;


    // =====================
    // 6) Floating & Security
    // =====================

    /** Whether floating is enabled (0 = No, 1 = Yes) */
    @NotNull(message = "isFloat is null")
    @Min(value = 0, message = "isFloat must be 0 or 1")
    @Max(value = 1, message = "isFloat must be 0 or 1")
    private Integer isFloat;

    /** Collection IP whitelist (comma-separated) */
    @NotBlank(message = "colWhiteIps is empty")
    private String colWhiteIps;

    /** Payout IP whitelist (comma-separated) */
    @NotBlank(message = "payWhiteIps is empty")
    private String payWhiteIps;


    // =====================
    // 7) Channel Configuration
    // =====================

    /** Channel IDs (comma-separated) */
    private String channelIds;

}

