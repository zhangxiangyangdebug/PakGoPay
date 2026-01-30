package com.pakgopay.timer.data;

public class OpsOrderContext extends OpsContext {
    /** Order type (0=collection,1=payout). */
    public Integer orderType;
    /** Period start time (epoch seconds). */
    public long startTime;
    /** Period end time (epoch seconds). */
    public long endTime;
}
