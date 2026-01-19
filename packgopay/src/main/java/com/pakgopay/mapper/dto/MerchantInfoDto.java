package com.pakgopay.mapper.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

@Data
public class MerchantInfoDto implements Serializable {

    private static final long serialVersionUID = 1L;

    /** User ID */
    private String userId;

    /** Parent ID (upper-level ID) */
    private String parentId;

    /** Merchant name */
    private String merchantName;

    /** support type: 0:collect 1:pay 2:collect/pay */
    private Integer supportType;

    /** Status (0 = Disabled, 1 = Enabled) */
    private Integer status;

    /** Risk level */
    private Integer riskLevel;

    /** Whether notification is enabled (0 = No, 1 = Yes) */
    private Integer notificationEnable;

    /** Creation time (Unix timestamp) */
    private Long createTime;

    /** Created by */
    private String createBy;

    /** Update time (Unix timestamp) */
    private Long updateTime;

    /** Updated by */
    private String updateBy;

    /** Collection rate */
    private BigDecimal collectionRate;

    /** Collection fixed fee */
    private BigDecimal collectionFixedFee;

    /** Collection maximum fee */
    private BigDecimal collectionMaxFee;

    /** Collection minimum fee */
    private BigDecimal collectionMinFee;

    /** Payout rate */
    private BigDecimal payRate;

    /** Payout fixed fee */
    private BigDecimal payFixedFee;

    /** Payout maximum fee */
    private BigDecimal payMaxFee;

    /** Payout minimum fee */
    private BigDecimal payMinFee;

    /** Version (for optimistic locking) */
    private Integer version;

    /** Whether floating is enabled (0 = No, 1 = Yes) */
    private Integer isFloat;

    /** float range */
    private BigDecimal floatRange;

    /** Collection IP whitelist (comma-separated) */
    private String colWhiteIps;

    /** Payout IP whitelist (comma-separated) */
    private String payWhiteIps;

    /** Channel IDs (comma-separated) */
    private String channelIds;

    // ---------------------- external info -----------------

    /** Merchant's agent infos */
    List<AgentInfoDto> agentInfos;

    /** Merchant's support currency */
    List<String> currencyList;
}
