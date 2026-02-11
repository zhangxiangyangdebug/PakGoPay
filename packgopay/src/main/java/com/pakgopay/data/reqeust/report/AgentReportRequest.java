package com.pakgopay.data.reqeust.report;

import lombok.Data;

@Data
public class AgentReportRequest extends BaseReportRequest{

    /** agent user id */
    private String agentName;
}
