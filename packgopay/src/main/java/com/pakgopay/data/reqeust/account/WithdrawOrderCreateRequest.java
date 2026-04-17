package com.pakgopay.data.reqeust.account;

import com.pakgopay.data.reqeust.BaseRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class WithdrawOrderCreateRequest extends BaseRequest {

    /**
     * User name
     */
    @NotBlank(message = "name is empty")
    private String name;

    /**
     * Withdraw amount
     */
    @NotNull(message = "amount is null")
    private BigDecimal amount;

    /**
     * Currency
     */
    @NotBlank(message = "currency is empty")
    private String currency;

    /**
     * User role: 1.merchant 2.agent
     */
    @NotNull(message = "userRole is null")
    private Integer userRole;

    /**
     * Wallet address
     */
    private String walletAddr;

    /**
     * Request ip
     */
    private String requestIp;

    /**
     * Remark
     */
    private String remark;
}