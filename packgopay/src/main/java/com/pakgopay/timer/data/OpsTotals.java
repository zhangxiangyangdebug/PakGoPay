package com.pakgopay.timer.data;

import java.math.BigDecimal;

public class OpsTotals {
    /** Total order count. */
    public long total;
    /** Success order count. */
    public long success;
    /** Agent commission amount. */
    public BigDecimal agentCommission = BigDecimal.ZERO;
}
