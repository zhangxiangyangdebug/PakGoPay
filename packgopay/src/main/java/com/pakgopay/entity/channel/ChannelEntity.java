package com.pakgopay.entity.channel;

import lombok.Data;

@Data
public class ChannelEntity {

    /**
     * Channel number
     */
    private String channelId;

    /**
     * paymentId number
     */
    private String paymentId;

    /**
     * currency
     */
    private String currency;

    /**
     * Channel name
     */
    private String channelName;

    /**
     * Enabled status
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
