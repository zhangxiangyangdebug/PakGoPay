package com.pakgopay.mapper.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class PaymentReportDto implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Payment ID */
    private Long paymentId;

    /** Payment name */
    private String paymentName;

    /** Order type: collection(0) / payout(1) */
    private Integer orderType;

    /** Total order quantity */
    private Long orderQuantity;

    /** Success order quantity */
    private Long successQuantity;

    /** Currency */
    private String currency;

    /** Merchant fee */
    private BigDecimal merchantFee;

    /** Order amount (sum) */
    private BigDecimal orderBalance;

    /** Platform profit */
    private BigDecimal orderProfit;

    /** Record time (unix timestamp seconds) */
    private Long recordDate;

    /** Create time (unix timestamp seconds) */
    private Long createTime;

    /** Update time (unix timestamp seconds) */
    private Long updateTime;
}
