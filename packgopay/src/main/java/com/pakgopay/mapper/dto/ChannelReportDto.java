package com.pakgopay.mapper.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class ChannelReportDto implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Channel ID */
    private Long channelId;

    /** Channel name */
    private String channelName;

    /** Order type: collection(0) / payout(1) */
    private Integer orderType;

    /** Remark */
    private String remark;

    /** currency */
    private String currency;

    /** Total orders (failed orders count per your comment) */
    private Long totalOrders;

    /** Success orders count */
    private Long successOrders;

    /** Order amount (sum) */
    private BigDecimal orderBalance;

    /** Merchant fee */
    private BigDecimal merchantFee;

    /** Platform profit */
    private BigDecimal orderProfit;

    /** Record date (unix timestamp seconds, usually 00:00:00 of the day) */
    private Long recordDate;

    /** Create time (unix timestamp seconds) */
    private Long createTime;

    /** Update time (unix timestamp seconds) */
    private Long updateTime;
}

