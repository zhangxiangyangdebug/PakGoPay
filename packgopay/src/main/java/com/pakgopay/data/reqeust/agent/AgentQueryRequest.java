package com.pakgopay.data.reqeust.agent;

import com.pakgopay.data.reqeust.ExportBaseRequest;
import lombok.Data;

@Data
public class AgentQueryRequest extends ExportBaseRequest {

    /**
     * Agent name
     */
    private String agentName;

    /**
     * account name
     */
    private String accountName;

    /**
     * status
     */
    private Integer status;
}
