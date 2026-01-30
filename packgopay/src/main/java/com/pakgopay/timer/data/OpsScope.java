package com.pakgopay.timer.data;

public class OpsScope {
    /** Scope type (0=all,1=merchant,2=agent). */
    public Integer scopeType;
    /** Scope id (merchant user id/agent user id or 0 for all). */
    public String scopeId;
}
