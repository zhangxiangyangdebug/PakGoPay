package com.pakgopay.data.reqeust.merchant;

import com.pakgopay.data.reqeust.BaseRequest;
import lombok.Data;

@Data
public class MerchantSecretKeyQueryRequest extends BaseRequest {
    /**
     * Merchant user id. Required for admin role.
     */
    private String merchantUserId;
}
