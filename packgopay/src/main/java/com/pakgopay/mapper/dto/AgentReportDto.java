package com.pakgopay.mapper.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class AgentReportDto implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Agent user ID */
    private String userId;

    /** Agent name */
    private String agentName;

    /** Order type: collection(0) / payout(1) */
    private Integer orderType;

    /** Order quantity */
    private Long orderQuantity;

    /** Success order quantity */
    private Long successQuantity;

    /** Currency */
    private String currency;

    /** Commission */
    private BigDecimal commission;

    /** Record date (unix timestamp seconds, usually 00:00:00 of the day) */
    private Long recordDate;

    /** Create time (unix timestamp seconds) */
    private Long createTime;

    /** Update time (unix timestamp seconds) */
    private Long updateTime;

    /** Remark */
    private String remark;
}

