package com.pakgopay.mapper.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class CollectionOrderDto implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Order ID */
    private String transactionNo;

    /** merchant order no */
    private String merchantOrderNo;

    /** Order amount */
    private BigDecimal amount;

    /** Actual order amount */
    private BigDecimal actualAmount;

    /** Floating amount */
    private BigDecimal floatingAmount;

    /** Currency code (e.g. USD, CNY) */
    private String currencyType;

    /** Merchant's User ID (not system) */
    private String merchantUserId;

    /** Callback token */
    private String callbackToken;

    /** Callback URL */
    private String callbackUrl;

    /** Callback retry times */
    private Integer callbackTimes;

    /** Last callback time */
    private Long lastCallbackTime;

    /** Merchant fixed fee */
    private BigDecimal merchantFixedFee;

    /** Merchant rate */
    private BigDecimal merchantRate;

    /** Merchant fee */
    private BigDecimal merchantFee;

    /** Level 1 agent rate */
    private BigDecimal agent1Rate;

    /** Level 1 agent fixed fee */
    private BigDecimal agent1FixedFee;

    /** Level 1 agent commission */
    private BigDecimal agent1Fee;

    /** Level 2 agent rate */
    private BigDecimal agent2Rate;

    /** Level 2 agent fixed fee */
    private BigDecimal agent2FixedFee;

    /** Level 2 agent commission */
    private BigDecimal agent2Fee;

    /** Level 3 agent rate */
    private BigDecimal agent3Rate;

    /** Level 3 agent fixed fee */
    private BigDecimal agent3FixedFee;

    /** Level 3 agent commission */
    private BigDecimal agent3Fee;

    /** Request IP */
    private String requestIp;

    /** Operation type: 1-system completed, 2-manual confirmation */
    private String operateType;

    /** Remark */
    private String remark;

    /** Create time */
    private Long createTime;

    /** Update time */
    private Long updateTime;

    /** Collection mode: 1-third-party, 2-system */
    private Integer collectionMode;

    /** Payment channel ID */
    private Long paymentId;

    /** Order type: 1-system order, 2-manual order */
    private Integer orderType;

    /** Callback status: 1-success, 2-failed, 3-pending */
    private String callbackStatus;

    /** Order status: 1-processing, 2-failed */
    private Integer orderStatus;

    /** Order request time */
    private Long requestTime;

    /** Callback success time */
    private Long successCallbackTime;

    /** Channel ID */
    private Long channelId;


}
