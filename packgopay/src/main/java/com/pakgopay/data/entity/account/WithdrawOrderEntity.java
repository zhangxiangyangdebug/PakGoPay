package com.pakgopay.data.entity.account;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class WithdrawOrderEntity {

    /**
     * Primary key id
     */
    private Long id;

    /**
     * Withdraw order no
     */
    private String withdrawNo;

    /**
     * Merchant/agent user id
     */
    private String merchantAgentId;

    /**
     * User role
     */
    private Integer userRole;

    /**
     * User name
     */
    private String name;

    /**
     * Currency
     */
    private String currency;

    /**
     * Withdraw amount
     */
    private BigDecimal amount;

    /**
     * Wallet address
     */
    private String walletAddr;

    /**
     * Request ip
     */
    private String requestIp;

    /**
     * Status: 0.pending 1.rejected 2.success
     */
    private Integer status;

    /**
     * Fail reason
     */
    private String failReason;

    /**
     * Remark
     */
    private String remark;

    /**
     * Create time
     */
    private Long createTime;

    /**
     * Update time
     */
    private Long updateTime;

    /**
     * Create by
     */
    private String createBy;

    /**
     * Update by
     */
    private String updateBy;

    /**
     * Query start time
     */
    private Long startTime;

    /**
     * Query end time
     */
    private Long endTime;

    /**
     * Page no
     */
    private Integer pageNo;

    /**
     * Page size
     */
    private Integer pageSize;

    /**
     * Offset
     */
    private Integer offset;
}