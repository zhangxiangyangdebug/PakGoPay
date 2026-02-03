package com.pakgopay.mapper.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class MerchantReportDto implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Merchant user ID */
    private String userId;

    /** Merchant name */
    private String merchantName;

    /** Order type: collection / payout */
    private Integer orderType;

    /** Currency */
    private String currency;

    /** Total order quantity */
    private Long orderQuantity;

    /** Success order quantity */
    private Long successQuantity;

    /** Merchant fee */
    private BigDecimal merchantFee;

    /** Agent level 1 fee */
    private BigDecimal agent1Fee;

    /** Agent level 2 fee */
    private BigDecimal agent2Fee;

    /** Agent level 3 fee */
    private BigDecimal agent3Fee;

    /** Platform profit */
    private BigDecimal orderProfit;

    /** Record date (unix timestamp in seconds, e.g. 20260101 00:00:00) */
    private Long recordDate;

    /** Create time (unix timestamp in seconds) */
    private Long createTime;

    /** Update time (unix timestamp in seconds) */
    private Long updateTime;
}
