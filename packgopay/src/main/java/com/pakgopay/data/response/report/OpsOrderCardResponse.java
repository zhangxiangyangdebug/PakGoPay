package com.pakgopay.data.response.report;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class OpsOrderCardResponse {

    /** Total order count. */
    private Long orderQuantity;

    /** Success order count. */
    private Long successQuantity;

    /** Success rate (success/total). */
    private BigDecimal successRate;

    /** Merchant fee. */
    private BigDecimal merchantFee;

    /** Valid merchant fee (success only). */
    private BigDecimal validMerchantFee;

    /** Frozen amount. */
    private BigDecimal frozenAmount;

    /** Available amount. */
    private BigDecimal availableAmount;
}
