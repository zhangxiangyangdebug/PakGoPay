package com.pakgopay.mapper.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CollectionOrderDto {

    /**
     * Primary key ID
     */
    private Long id;

    /**
     * Merchant ID
     */
    private Long merchantId;

    /**
     * Order ID
     */
    private Long orderId;

    /**
     * Order status
     */
    private Integer orderStatus;

    /**
     * Order type (e.g. collection / payout)
     */
    private Integer orderType;

    /**
     * Callback status
     */
    private Integer callbackStatus;

    /**
     * Callback request times
     */
    private Integer requestTimes;

    /**
     * Remark
     */
    private String remark;

    /**
     * Create time
     */
    private LocalDateTime createTime;

    /**
     * Update time
     */
    private LocalDateTime updateTime;

}
