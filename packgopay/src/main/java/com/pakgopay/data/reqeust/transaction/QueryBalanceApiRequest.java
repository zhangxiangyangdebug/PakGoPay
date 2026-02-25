package com.pakgopay.data.reqeust.transaction;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Query balance request payload for transaction API.
 */
@Data
public class QueryBalanceApiRequest {
    /** Merchant ID */
    @NotBlank(message = "merhcantId is empty")
    private String merhcantId;

    /** Request signature */
    @NotBlank(message = "sign is empty")
    private String sign;
}
