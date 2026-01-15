package com.pakgopay.data.entity.agent;

import lombok.Data;

@Data
public class AgentAccountInfoEntity {

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

    /** Page number (start from 1) */
    private Integer pageNo;

    /** Page size */
    private Integer pageSize;

    public Integer getOffset() {
        return (pageNo - 1) * pageSize;
    }
}
