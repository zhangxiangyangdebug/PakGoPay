package com.pakgopay.mapper.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class OpsOrderDailyDto implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Primary key. */
    private Long id;

    /** Report date (yyyy-MM-dd). */
    private LocalDate reportDate;

    /** Order type (0=collection,1=payout). */
    private Integer orderType;

    /** Currency code. */
    private String currency;

    /** Scope type (0=all,1=merchant,2=agent). */
    private Integer scopeType;

    /** Scope id (merchant user id/agent user id or 0 for all). */
    private String scopeId;

    /** Total order count. */
    private Long orderQuantity;

    /** Success order count. */
    private Long successQuantity;

    /** Failed order count. */
    private Long failQuantity;

    /** Success rate. */
    private BigDecimal successRate;

    /** Agent commission amount. */
    private BigDecimal agentCommission;

    /** Create time (epoch seconds). */
    private Long createTime;

    /** Update time (epoch seconds). */
    private Long updateTime;
}
