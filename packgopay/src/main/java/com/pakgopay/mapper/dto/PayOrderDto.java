package com.pakgopay.mapper.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class PayOrderDto implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Order ID */
    private String orderId;

    /** Order amount */
    private BigDecimal amount;

    /** Actual amount (DB type is varchar(10)) */
    private BigDecimal actualAmount;

    /** Callback token */
    private String callbackToken;

    /** Callback URL */
    private String callbackUrl;

    /** Callback retry times */
    private Integer callbackTimes;

    /** Last callback time (DB column: last_callbak_time) */
    private LocalDateTime lastCallbackTime;

    /** Callback success time */
    private LocalDateTime successCallbackTime;

    /** Callback status: 1-success, 2-failed, 3-pending */
    private Integer callbackStatus;

    /** User ID */
    private String userId;

    /** Merchant fixed fee */
    private BigDecimal merchantFixedFee;

    /** Merchant rate */
    private BigDecimal merchantRate;

    /** Merchant fee/commission */
    private BigDecimal merchantFee;

    /** Level 1 agent fixed fee */
    private BigDecimal agent1FixedFee;

    /** Level 1 agent rate */
    private BigDecimal agent1Rate;

    /** Level 1 agent fee */
    private BigDecimal agent1Fee;

    /** Level 2 agent fixed fee (DB column: agnet2_fixed_fee) */
    private BigDecimal agent2FixedFee;

    /** Level 2 agent rate (DB column: agnet2_rate) */
    private BigDecimal agent2Rate;

    /** Level 2 agent fee (DB column: agnet2_fee) */
    private BigDecimal agent2Fee;

    /** Level 3 agent fixed fee (DB column: agnet3_fixed_fee) */
    private BigDecimal agent3FixedFee;

    /** Level 3 agent rate (DB column: agnet3_rate) */
    private BigDecimal agent3Rate;

    /** Level 3 agent fee (DB column: agnet3_fee) */
    private BigDecimal agent3Fee;

    /** Request IP */
    private String requestIp;

    /** Create time */
    private LocalDateTime createTime;

    /** Update time */
    private LocalDateTime updateTime;

    /** Request time */
    private LocalDateTime requestTime;

    /** Order type: 1-system, 2-manual */
    private Integer orderType;

    /** Payment mode: 1-third-party, 2-system */
    private Integer paymentMode;

    /** Merchant ID */
    private String merchantId;

    /** Payment channel ID */
    private Long paymentId;

    /** Channel ID */
    private Long channelId;

    /** Order status: 1-processing, 2-failed (DB type is varchar) */
    private String orderStatus;

    /** Remark */
    private String remark;

    /** Currency code (e.g. USD, CNY) */
    private String currencyType;

    /** Operate type: 1-system completed, 2-manual confirmation */
    private Integer operateType;

    // getters / setters (use Lombok @Data if you prefer)
}

