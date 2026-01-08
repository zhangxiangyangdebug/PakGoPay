package com.pakgopay.mapper.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class CurrencyReportDto implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Currency code (English abbreviation) */
    private String currency;

    /** Order type: 1-collection, 2-payout */
    private Integer orderType;

    /** Order amount (sum) */
    private BigDecimal orderBalance;

    /** Total order quantity */
    private Long orderQuantity;

    /** Success order quantity */
    private Long successQuantity;

    /** Create time (unix timestamp seconds) */
    private Long createTime;

    /** Update time (unix timestamp seconds) */
    private Long updateTime;

    /** Record date (unix timestamp seconds, usually 00:00:00 of the day) */
    private Long recordDate;

    /** Remark */
    private String remark;
}

