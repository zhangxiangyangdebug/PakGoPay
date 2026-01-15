package com.pakgopay.data.entity.merchant;

import lombok.Data;

@Data
public class MerchantEntity {
    /**
     * merchant name
     */
    private String merchantName;

    /**
     * merchant userName
     */
    private String merchantUserName;

    /**
     * merchant userId
     */
    private String merchantUserId;

    /**
     * enable status
     */
    private Integer status;

    /**
     * merchant's agent userId
     */
    private String parentAgentId;

    /** Page number (start from 1) */
    private Integer pageNo;

    /** Page size */
    private Integer pageSize;

    public Integer getOffset() {
        return (pageNo - 1) * pageSize;
    }
}
