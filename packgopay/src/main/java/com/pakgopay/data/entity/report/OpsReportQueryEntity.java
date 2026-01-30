package com.pakgopay.data.entity.report;

import lombok.Data;

@Data
public class OpsReportQueryEntity {

    /** Record date base (day: yyyy-MM-dd, month: yyyy-MM, year: yyyy). */
    private String recordDate;

    /** Currency code. */
    private String currency;

    /** Scope type (0=all,1=merchant,2=agent). */
    private Integer scopeType;

    /** Scope id (merchant user id/agent user id or 0 for all). */
    private String scopeId;
}
