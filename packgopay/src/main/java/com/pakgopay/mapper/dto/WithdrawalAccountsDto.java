package com.pakgopay.mapper.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class WithdrawalAccountsDto implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Primary key ID */
    private Long id;

    /** Merchant/agent user_id */
    private String merchantAgentId;

    /** user name */
    private String userName;

    /** Agent/Merchant name */
    private String name;

    /** Wallet address */
    private String walletAddr;

    /** Wallet name */
    private String walletName;

    /** Enabled status: 0-disabled, 1-enabled */
    private Integer status;

    /** Create time (unix seconds) */
    private Long createTime;

    /** Created by */
    private String createBy;

    /** Update time (unix seconds) */
    private Long updateTime;

    /** Updated by */
    private String updateBy;

    /** Remark */
    private String remark;
}

