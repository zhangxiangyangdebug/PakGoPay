package com.pakgopay.timer.data;

import java.math.BigDecimal;
import java.time.LocalDate;

public class OpsRecord {
    /** Report period. */
    public OpsPeriod period;
    /** Report date (daily). */
    public LocalDate reportDate;
    /** Report month (yyyy-MM). */
    public String reportMonth;
    /** Report year (yyyy). */
    public String reportYear;
    /** Order type (0=collection,1=payout). */
    public Integer orderType;
    /** Currency code. */
    public String currency;
    /** Scope type (0=all,1=merchant,2=agent). */
    public Integer scopeType;
    /** Scope id (merchant user id/agent user id or 0 for all). */
    public String scopeId;
    /** Total order count. */
    public long orderQuantity;
    /** Success order count. */
    public long successQuantity;
    /** Failed order count. */
    public long failQuantity;
    /** Agent commission amount. */
    public BigDecimal agentCommission = BigDecimal.ZERO;
}
