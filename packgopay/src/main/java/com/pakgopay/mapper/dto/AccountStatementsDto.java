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

    /** Auto-increment primary key */
    private Long id;

    /** Sequence within the same userId+currency */
    private Long accountSeq;

    /** Business serial no, e.g. SE... */
    private String serialNo;

    /** Order transaction no */
    private String transactionNo;

    /** Order type: 11/21/22/23/31/32/33/41/42 */
    private Integer orderType;

    /** Change amount */
    private BigDecimal amount;

    /** Status: 0-Pending apply, 1-Completed, 2-Failed, 3-Applied waiting snapshot */
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

    /** Request IP */
    private String requestIp;

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
