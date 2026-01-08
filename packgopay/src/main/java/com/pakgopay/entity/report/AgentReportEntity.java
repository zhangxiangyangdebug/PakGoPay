package com.pakgopay.entity.report;

import lombok.Data;

@Data
public class AgentReportEntity extends BaseReportEntity {

    /** Optional: agent user ID */
    private String userId;
}
