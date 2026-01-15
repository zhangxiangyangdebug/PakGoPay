package com.pakgopay.common.reqeust.agent;

import com.pakgopay.common.reqeust.ExportBaseRequest;
import lombok.Data;

@Data
public class AgentAccountQueryRequest extends ExportBaseRequest {

    /**
     * Agent name
     */
    private String agentName;

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
