package com.pakgopay.common.reqeust.report;

import lombok.Data;

@Data
public class AgentReportRequest extends BaseReportRequest{

    /** agent user id */
    private String agentId;
}
