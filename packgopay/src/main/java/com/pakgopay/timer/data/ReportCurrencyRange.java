package com.pakgopay.timer.data;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Currency time range for report aggregation.
 */
@Data
@AllArgsConstructor
public class ReportCurrencyRange {

    /** currency code */
    private String currency;

    /** start time (epoch seconds) */
    private Long startTime;

    /** end time (epoch seconds) */
    private Long endTime;
}
