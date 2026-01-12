package com.pakgopay.common.reqeust.channel;

import com.pakgopay.common.reqeust.ExportBaseRequest;
import lombok.Data;

@Data
public class PaymentRequest extends ExportBaseRequest {
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
}
