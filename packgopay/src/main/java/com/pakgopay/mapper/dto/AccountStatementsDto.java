package com.pakgopay.mapper.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Table: account_statements
 * Account statements DTO
 */
@Data
public class AccountStatementsDto implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Statement ID (Snowflake ID) */
    private String id;

    /** Order type: 1-Recharge, 2-Withdraw, 3-Manual adjustment */
    private Integer orderType;

    /** Change amount */
    private BigDecimal amount;

    /** Status: 0-Pending, 1-Completed, 2-Failed */
    private Integer status;

    /** Available balance before change */
    private BigDecimal availableBalanceBefore;

    /** Available balance after change */
    private BigDecimal availableBalanceAfter;

    /** Frozen balance before change */
    private BigDecimal frozenBalanceBefore;

    /** Frozen balance after change */
    private BigDecimal frozenBalanceAfter;

    /** Total balance before change (column name: total_balance_before) */
    private BigDecimal totalBalanceBefore;

    /** Total balance after change */
    private BigDecimal totalBalanceAfter;

    /** Wallet address */
    private String walletAddr;

    /** User ID */
    private String userId;

    /** User role */
    private Integer userRole;

    /** Merchant or agent name */
    private String name;

    /** Currency */
    private String currency;

    /** Create timestamp */
    private Long createTime;

    /** Created by */
    private String createBy;

    /** Update timestamp */
    private Long updateTime;

    /** Updated by */
    private String updateBy;

    /** Remark */
    private String remark;
}
