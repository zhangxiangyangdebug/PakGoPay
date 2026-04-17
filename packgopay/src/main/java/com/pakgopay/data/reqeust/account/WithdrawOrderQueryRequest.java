package com.pakgopay.data.reqeust.account;

import com.pakgopay.data.reqeust.ExportBaseRequest;
import lombok.Data;

@Data
public class WithdrawOrderQueryRequest extends ExportBaseRequest {

    /**
     * Withdraw order no
     */
    private String withdrawNo;

    /**
     * Merchant/agent user id
     */
    private String merchantAgentId;

    /**
     * User role: 1.merchant 2.agent
     */
    private Integer userRole;

    /**
     * Currency
     */
    private String currency;

    /**
     * Withdraw status: 0.pending 1.rejected 2.success
     */
    private Integer status;

    /**
     * Request ip
     */
    private String requestIp;

    /**
     * Optional: create_time >= startTime (unix seconds)
     */
    private Long startTime;

    /**
     * Optional: create_time < endTime (unix seconds)
     */
    private Long endTime;
}
