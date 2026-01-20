package com.pakgopay.data.reqeust.account;

import com.pakgopay.data.reqeust.BaseRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class AccountStatementEditRequest extends BaseRequest  {

    /**
     * order Id
     */
    @NotBlank(message = "id is empty")
    private String id;

    /**
     * merchant user id
     */
    @NotBlank(message = "merchantAgentId is empty")
    private String merchantAgentId;

    /**
     * amount
     */
    @NotNull(message = "amount is null")
    private BigDecimal amount;

    /**
     * is agree?
     */
    private boolean isAgree;

    /**
     * remark
     */
    private String remark;
}
