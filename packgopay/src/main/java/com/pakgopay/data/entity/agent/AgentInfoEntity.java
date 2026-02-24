package com.pakgopay.data.entity.agent;

import lombok.Data;

@Data
public class AgentInfoEntity {

    /**
     * Agent name
     */
    private String agentName;

    /**
     * Account name
     */
    private String accountName;

    /**
     * status
     */
    private Integer status;

    /**
     * user id
     */
    private String userId;

    /**
     * top agent id
     */
    private String topAgentId;

    /**
     * max level
     */
    private Integer maxLevel;

    /** Page number (start from 1) */
    private Integer pageNo;

    /** Page size */
    private Integer pageSize;

    public Integer getOffset() {
        return (pageNo - 1) * pageSize;
    }
}
