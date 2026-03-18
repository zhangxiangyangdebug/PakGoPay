package com.pakgopay.data.reqeust.bankCode;

import com.pakgopay.data.reqeust.BaseRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class PaymentBankCodeUpdateRequest extends BaseRequest {

    @NotNull(message = "paymentId is null")
    private Long paymentId;

    @NotBlank(message = "currencyCode is blank")
    private String currencyCode;

    @NotEmpty(message = "items is empty")
    @Valid
    private List<PaymentBankCodeItemRequest> items;
}
