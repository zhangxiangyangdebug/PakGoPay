package com.pakgopay.data.reqeust.account;

import com.pakgopay.data.reqeust.BaseRequest;
import lombok.Data;

@Data
public class AccountEditRequest extends BaseRequest {

    /**
     * id
     */
    private String id;

    /**
     * account name
     */
    private String walletAddr;

    /**
     * status
     */
    private Integer status;
}
