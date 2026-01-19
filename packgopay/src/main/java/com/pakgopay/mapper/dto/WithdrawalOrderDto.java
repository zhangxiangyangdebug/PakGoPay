package com.pakgopay.mapper.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class WithdrawalOrderDto implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Primary key ID */
    private Long id;

    /** merchant ID */
    private String merchantId;

    /** merchant name */
    private String merchantName;

    /** Balance before operation */
    private BigDecimal beforeAmount;

    /** Operation amount */
    private BigDecimal amount;

    /** Balance after operation */
    private BigDecimal afterAmount;

    /** Currency code (e.g. USDT, USD, PKR) */
    private String currency;

    /** Status: 1-Pending, 2-Failed, 3-Completed */
    private String status;

    /** Wallet address (e.g. USDT address) */
    private String walletAddr;

    /** Create timestamp */
    private Long createTime;

    /** Created by */
    private String createBy;

    /** Update timestamp */
    private Long updateTime;

    /** Updated by (user id) */
    private Long updateBy;

    /** Remark */
    private String remark;

    /** Operated by */
    private String operateBy;
}
