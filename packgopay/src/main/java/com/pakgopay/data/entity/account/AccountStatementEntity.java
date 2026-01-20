package com.pakgopay.data.entity.account;

import lombok.Data;

@Data
public class AccountStatementEntity {

    /**
     * system transaction id
     */
    private String id;

    /**
     * merchant/agent user Id
     */
    private String userId;

    /**
     * order type
     */
    private Integer orderType;


    /**
     * currency
     */
    private String currency;

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
