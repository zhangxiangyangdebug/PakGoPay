package com.pakgopay.common.reqeust.channel;

import com.pakgopay.common.reqeust.ExportBaseRequest;
import lombok.Data;

@Data
public class PaymentQueryRequest extends ExportBaseRequest {
    /**
     * Payment name
     */
    private String paymentName;

    /** Whether it is a third-party channel: 0-no, 1-yes (varchar in DB) */
    private String isThird;

    /** Payment type: 1-app payment, 2-bank card payment */
    private String paymentType;

    /** Currency */
    private String currency;

    /**
     * Enabled status
     */
    private Integer status;
}
