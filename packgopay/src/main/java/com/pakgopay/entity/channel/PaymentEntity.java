package com.pakgopay.entity.channel;

import lombok.Data;

@Data
public class PaymentEntity {

    /**
     * Payment name
     */
    private String paymentName;

    /** support type: 0:collect 1:pay 2:collect/pay */
    private String supportType;

    /** Payment type: 1-app payment, 2-bank card payment */
    private String paymentType;

    /** Currency */
    private String currency;

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
