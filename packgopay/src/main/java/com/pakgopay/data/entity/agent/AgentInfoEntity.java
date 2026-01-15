package com.pakgopay.data.entity.agent;

import lombok.Data;

@Data
public class AgentInfoEntity {

    /**
     * Agent name
     */
    private String agentName;

    /**
     * user name
     */
    private String userName;

    /**
     * status
     */
    private Integer Status;

    /** Page number (start from 1) */
    private Integer pageNo;

    /** Page size */
    private Integer pageSize;

    public Integer getOffset() {
        return (pageNo - 1) * pageSize;
    }
}
