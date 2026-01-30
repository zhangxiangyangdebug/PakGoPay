package com.pakgopay.data.reqeust.report;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class OpsReportRequest {

    /** Record date base (day: yyyy-MM-dd, month: yyyy-MM, year: yyyy). */
    @NotBlank(message = "recordDate is empty")
    private String recordDate;

    /** Currency code. */
    @NotBlank(message = "currency is empty")
    private String currency;

    /** Scope type (0=all,1=merchant,2=agent). */
    @NotNull(message = "scopeType is null")
    private Integer scopeType;

    /** Scope id (merchant user id/agent user id or 0 for all). */
    @NotBlank(message = "scopeId is empty")
    private String scopeId;
}
