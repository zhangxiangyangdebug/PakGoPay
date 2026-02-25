package com.pakgopay.data.reqeust.transaction;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Query order request payload for transaction API.
 */
@Data
public class QueryOrderApiRequest {
    /** Merchant ID */
    @NotBlank(message = "merchantId is empty")
    private String merchantId;

    /** Merchant order number */
    @NotBlank(message = "merchantOrderNo is empty")
    private String merchantOrderNo;

    /** Order type: COLL / PAY */
    private String orderType;

    /** Request signature */
    @NotBlank(message = "sign is empty")
    private String sign;
}
