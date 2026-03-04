package com.pakgopay.data.response.merchant;

import lombok.Data;

@Data
public class MerchantSecretKeyResponse {
    private String merchantUserId;
    private String apiKey;
    private String signKey;
}

