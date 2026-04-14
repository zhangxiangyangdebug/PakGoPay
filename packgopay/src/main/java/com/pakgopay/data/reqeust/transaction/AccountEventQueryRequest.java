package com.pakgopay.data.reqeust.transaction;

import com.pakgopay.data.reqeust.BaseRequest;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class AccountEventQueryRequest extends BaseRequest {

    @NotBlank(message = "transactionNo is empty")
    private String transactionNo;
}
