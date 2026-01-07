package com.pakgopay.common.entity;

import lombok.Data;

@Data
public class BaseReportEntity {
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
