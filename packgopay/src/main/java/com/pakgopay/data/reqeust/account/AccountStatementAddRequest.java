package com.pakgopay.data.reqeust.account;

import com.pakgopay.data.reqeust.BaseRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class AccountStatementAddRequest extends BaseRequest  {

    /**
     * merchant user id
     */
    @NotBlank(message = "merchantAgentId is empty")
    private String merchantAgentId;

    /**
     * merchant name
     */
    @NotBlank(message = "merchantAgentName is empty")
    private String merchantAgentName;

    /**
     * amount
     */
    @NotNull(message = "amount is null")
    private BigDecimal amount;

    /**
     * currency
     */
    @NotBlank(message = "currency is empty")
    private String currency;

    /**
     * orderType
     */
    @NotNull(message = "orderType is null")
    private Integer orderType;

    /**
     * walletAddr
     */
    private String walletAddr;

    /**
     * remark
     */
    private String remark;
}
