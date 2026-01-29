package com.pakgopay.data.reqeust.agent;

import com.pakgopay.data.reqeust.BaseRequest;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class AgentAddRequest extends BaseRequest {

    // =====================
    // 1) Identity & Account Info
    // =====================

    /** Agent name */
    @NotBlank(message = "agentName is empty")
    private String agentName;

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

    /** Parent agent ID (nullable for top-level agent) */
    private String parentId;

    /** Top agent ID */
    private String topAgentId;

    /** Agent level */
    @NotNull(message = "level is null")
    @Min(value = 1, message = "level must >= 1")
    @Max(value = 3, message = "level must <= 3")
    private Integer level;

    /** Agent's channel ids */
    @NotNull(message = "channelIds is null")
    private List<Integer> channelIds;


    // =====================
    // 2) Status
    // =====================

    /** Status: 0-disabled, 1-enabled */
    @NotNull(message = "status is null")
    @Min(value = 0, message = "status must be 0 or 1")
    @Max(value = 1, message = "status must be 0 or 1")
    private Integer status;


    // =====================
    // 3) Contact Info
    // =====================

    /** Contact name */
    @NotBlank(message = "contactName is empty")
    private String contactName;

    /** Contact email */
    @NotBlank(message = "contactEmail is empty")
    @Email(message = "contactEmail format error")
    private String contactEmail;

    /** Contact phone */
    @NotBlank(message = "contactPhone is empty")
    @Pattern(regexp = "^[0-9+\\-]{6,20}$", message = "contactPhone format error")
    private String contactPhone;


    // =====================
    // 4) Collection Configuration
    // =====================

    /** Collection rate (0 ~ 1) */
    @NotNull(message = "collectionRate is null")
    private BigDecimal collectionRate;

    /** Collection fixed fee */
    @NotNull(message = "collectionFixedFee is null")
    @DecimalMin(value = "0", inclusive = true, message = "collectionFixedFee must >= 0")
    private BigDecimal collectionFixedFee;

    /** Collection max fee */
    @NotNull(message = "collectionMaxFee is null")
    @DecimalMin(value = "0", inclusive = true, message = "collectionMaxFee must >= 0")
    private BigDecimal collectionMaxFee;

    /** Collection min fee */
    @NotNull(message = "collectionMinFee is null")
    @DecimalMin(value = "0", inclusive = true, message = "collectionMinFee must >= 0")
    private BigDecimal collectionMinFee;


    // =====================
    // 5) Payout Configuration
    // =====================

    /** Payout rate (0 ~ 1) */
    @NotNull(message = "payRate is null")
    private BigDecimal payRate;

    /** Payout fixed fee */
    @NotNull(message = "payFixedFee is null")
    @DecimalMin(value = "0", inclusive = true, message = "payFixedFee must >= 0")
    private BigDecimal payFixedFee;

    /** Payout max fee */
    @NotNull(message = "payMaxFee is null")
    @DecimalMin(value = "0", inclusive = true, message = "payMaxFee must >= 0")
    private BigDecimal payMaxFee;

    /** Payout min fee */
    @NotNull(message = "payMinFee is null")
    @DecimalMin(value = "0", inclusive = true, message = "payMinFee must >= 0")
    private BigDecimal payMinFee;


    // =====================
    // 6) Security / Whitelist
    // =====================

    /** Login white ips (comma separated) */
    @NotBlank(message = "loginIps is empty")
    private String loginIps;

    /** withdrawal white ips (comma separated) */
    @NotBlank(message = "withdrawalIps is empty")
    private String withdrawalIps;

    // =====================
    // 7) Other
    // =====================

    /** Remark */
    private String remark;
}

