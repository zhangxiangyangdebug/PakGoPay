package com.pakgopay.mapper.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CollectionOrderDto {

    /**
     * Merchant ID
     */
    private Long merchantId;

    /**
     * Order ID
     */
    private String orderId;

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
