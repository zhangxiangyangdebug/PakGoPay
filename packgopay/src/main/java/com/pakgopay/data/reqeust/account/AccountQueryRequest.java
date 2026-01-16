package com.pakgopay.data.reqeust.account;

import com.pakgopay.data.reqeust.ExportBaseRequest;
import lombok.Data;

@Data
public class AccountQueryRequest extends ExportBaseRequest {

    /**
     * Agent name
     */
    private String name;

    /**
     * account name
     */
    private String walletAddr;

    /** Optional: create_time >= startTime (unix seconds) */
    private Long startTime;

    /** Optional: create_time < endTime (unix seconds) */
    private Long endTime;


    /**
     * is need page card data
     */
    private Boolean isNeedCardData = false;
}
