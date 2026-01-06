package com.pakgopay.mapper.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class BalanceDto implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Merchant user ID */
    private String userId;

    /** Currency */
    private String currency;

    /** Available balance */
    private BigDecimal availableBalance;

    /** Frozen balance */
    private BigDecimal frozenBalance;

    /** Total balance */
    private BigDecimal totalBalance;

    /** Create time */
    private Long createTime;

    /** Update time */
    private Long updateTime;

    /** Remark */
    private String remark;
}

