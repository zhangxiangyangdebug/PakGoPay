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

    /** Page number (start from 1) */
    private Integer pageNo;

    /** Page size */
    private Integer pageSize;

    public Integer getOffset() {
        return (pageNo - 1) * pageSize;
    }
}
