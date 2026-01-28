package com.pakgopay.data.reqeust.account;

import com.pakgopay.data.reqeust.ExportBaseRequest;
import lombok.Data;

@Data
public class AccountStatementQueryRequest extends ExportBaseRequest {

    /**
     * system transaction id
     */
    private String id;

    /**
     * merchant/agent user Id
     */
    private String merchantAgentId;

    /**
     * order type
     */
    private Integer orderType;


    /**
     * currency
     */
    private String currency;

    /** Optional: create_time >= startTime (unix seconds) */
    private Long startTime;

    /** Optional: create_time < endTime (unix seconds) */
    private Long endTime;
}
