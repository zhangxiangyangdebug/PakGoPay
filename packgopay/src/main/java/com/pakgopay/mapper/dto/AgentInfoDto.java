package com.pakgopay.mapper.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class AgentInfoDto implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Agent login user ID */
    private String userId;

    /** Agent number */
    private String agentNo;

    /** Agent name */
    private String agentName;

    /** Parent agent ID */
    private String parentId;

    /** Status: 0-disabled, 1-enabled */
    private Integer status;

    /** Contact name */
    private String contactName;

    /** Contact email */
    private String contactEmail;

    /** Contact phone */
    private String contactPhone;

    /** Balance record ID */
    private Long balanceId;

    /** Agent level */
    private Integer level;

    /** Collection rate */
    private BigDecimal collectionRate;

    /** Collection fixed fee */
    private BigDecimal collectionFixedFee;

    /** Collection max fee */
    private BigDecimal collectionMaxFee;

    /** Collection min fee */
    private BigDecimal collectionMinFee;

    /** Payout rate */
    private BigDecimal payRate;

    /** Payout fixed fee */
    private BigDecimal payFixedFee;

    /** Payout max fee */
    private BigDecimal payMaxFee;

    /** Payout min fee */
    private BigDecimal payMinFee;

    /** Create time */
    private LocalDateTime createTime;

    /** Created by */
    private String createBy;

    /** Update time */
    private LocalDateTime updateTime;

    /** Updated by (DB column: upeate_by) */
    private String updateBy;

    /** Version */
    private Integer version;

    /** Remark */
    private String remark;
}
