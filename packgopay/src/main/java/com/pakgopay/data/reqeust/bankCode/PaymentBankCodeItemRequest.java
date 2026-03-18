package com.pakgopay.data.reqeust.bankCode;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PaymentBankCodeItemRequest {

    @NotBlank(message = "bankCode is blank")
    private String bankCode;

    @NotNull(message = "supportType is null")
    @Min(value = 0, message = "supportType must be 0, 1, 2")
    @Max(value = 2, message = "supportType must be 0, 1, 2")
    private Integer supportType;

    @NotNull(message = "status is null")
    @Min(value = 0, message = "status must be 0 or 1")
    @Max(value = 1, message = "status must be 0 or 1")
    private Integer status;
}
