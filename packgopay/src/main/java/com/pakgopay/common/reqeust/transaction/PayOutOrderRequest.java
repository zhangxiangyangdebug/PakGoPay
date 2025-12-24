package com.pakgopay.common.reqeust.transaction;

import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class PayOutOrderRequest extends OrderBaseRequest {
    /**
     * Beneficiary bank / payment institution code
     * Optional when channelParams is provided
     */
    private String bankCode;

    /**
     * Beneficiary account name
     * Optional when channelParams is provided
     */
    private String accountName;

    /**
     * Beneficiary account number
     * Optional when channelParams is provided
     */
    private String accountNo;

}
