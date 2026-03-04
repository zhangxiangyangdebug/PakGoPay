package com.pakgopay.mapper.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ReportCardSummaryDto {

    /** Matched rows count for report query */
    private Long totalNumber;

    /** Sum of order_balance */
    private BigDecimal total;

    /** Sum of success_order_balance */
    private BigDecimal successOrderBalance;
}
