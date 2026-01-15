package com.pakgopay.data.reqeust.merchant;

import com.pakgopay.data.reqeust.ExportBaseRequest;
import lombok.Data;

@Data
public class MerchantQueryRequest extends ExportBaseRequest {

    /**
     * merchant name
     */
    private String merchantName;

    /**
     * merchant userName
     */
    private String merchantUserName;

    /**
     * merchant userId
     */
    private String merchantUserId;

    /**
     * enable status
     */
    private Integer status;

    /**
     * merchant's agent userId
     */
    private String parentAgentId;
}
