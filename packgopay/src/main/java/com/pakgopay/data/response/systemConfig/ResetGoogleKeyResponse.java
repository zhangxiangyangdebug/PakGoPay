package com.pakgopay.data.response.systemConfig;

import lombok.Data;

@Data
public class ResetGoogleKeyResponse {
    private String qrCode;

    private String secretKey;
}
