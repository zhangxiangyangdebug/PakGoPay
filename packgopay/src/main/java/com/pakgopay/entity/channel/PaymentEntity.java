package com.pakgopay.entity.channel;

import lombok.Data;

@Data
public class PaymentEntity {

    /**
     * Payment number
     */
    private String paymentId;

    /**
     * Payment name
     */
    private String paymentName;

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
