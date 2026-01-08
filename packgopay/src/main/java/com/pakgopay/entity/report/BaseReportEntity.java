package com.pakgopay.entity.report;

import lombok.Data;

@Data
public class BaseReportEntity {

    /** Optional: order type: collection(0) / payout(1) */
    private Integer orderType;

    /** currency */
    private String currency;

    /** Optional: record_date >= startTime (unix seconds) */
    private Long startTime;

    /** Optional: record_date < endTime (unix seconds) */
    private Long endTime;

    /** Page number (start from 1) */
    private Integer pageNo;

    /** Page size */
    private Integer pageSize;

    public Integer getOffset() {
        return (pageNo - 1) * pageSize;
    }
}
