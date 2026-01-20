package com.pakgopay.data.reqeust.account;

import com.pakgopay.data.reqeust.BaseRequest;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class AccountRechargeRequest extends BaseRequest  {

    /**
     * merchant user id
     */
    @NotBlank(message = "merchantId is empty")
    private String merchantId;

    /**
     * merchant name
     */
    @NotBlank(message = "merchantName is empty")
    private String merchantName;


    /**
     * amount
     */
    @NotNull(message = "amount is null")
    @Min(value = 0, message = "amount must >= 0")
    private BigDecimal amount;

    /**
     * currency
     */
    @NotBlank(message = "currency is empty")
    private String currency;

    /**
     * remark
     */
    private String remark;
}
