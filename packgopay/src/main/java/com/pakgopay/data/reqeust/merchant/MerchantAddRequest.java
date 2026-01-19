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
    private Integer riskLevel;

    /** Whether notification is enabled (0 = No, 1 = Yes) */
    private Integer notificationEnable;


    // =====================
    // 4) Collection Fee Configuration
    // =====================

    /** Collection rate */
    private BigDecimal collectionRate;

    /** Collection fixed fee */
    private BigDecimal collectionFixedFee;

    /** Collection maximum fee */
    private BigDecimal collectionMaxFee;

    /** Collection minimum fee */
    private BigDecimal collectionMinFee;

    /** Collection IP whitelist (comma-separated) */
    private String colWhiteIps;

    // =====================
    // 5) Payout Fee Configuration
    // =====================

    /** Payout rate */
    private BigDecimal payRate;

    /** Payout fixed fee */
    private BigDecimal payFixedFee;

    /** Payout maximum fee */
    private BigDecimal payMaxFee;

    /** Payout minimum fee */
    private BigDecimal payMinFee;

    /** Payout IP whitelist (comma-separated) */
    private String payWhiteIps;

    // =====================
    // 6) Floating / Security
    // =====================

    /** Whether floating is enabled (0 = No, 1 = Yes) */
    @NotNull(message = "isFloat is null")
    @Min(value = 0, message = "isFloat must be 0 or 1")
    @Max(value = 1, message = "isFloat must be 0 or 1")
    private Integer isFloat;

    /** Login white ips (comma separated) */
    @NotBlank(message = "loginIps is empty")
    private String loginIps;

    // =====================
    // 7) Channel Configuration
    // =====================

    /** Channel IDs (comma-separated) */
    private String channelIds;

}

