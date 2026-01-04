package com.pakgopay.mapper.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class BalanceDto implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Merchant user ID */
    private String userId;

    /** Available balance */
    private BigDecimal availableBalance;

    /** Frozen balance */
    private BigDecimal frozenBalance;

    /** Total balance */
    private BigDecimal totalBalance;

    /** Create time */
    private LocalDateTime createTime;

    /** Update time */
    private LocalDateTime updateTime;

    /** Remark */
    private String remark;
}

