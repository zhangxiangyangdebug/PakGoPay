package com.pakgopay.data.reqeust.transaction;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class MerchantAvailableChannelRequest {

    @NotBlank(message = "merchantId is required")
    private String merchantId;
}
