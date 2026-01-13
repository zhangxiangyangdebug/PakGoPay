package com.pakgopay.common.reqeust.channel;

import com.pakgopay.common.reqeust.ExportBaseRequest;
import lombok.Data;

@Data
public class PaymentQueryRequest extends ExportBaseRequest {
    /**
     * Payment number
     */
    private String paymentNo;

    /**
     * Payment name
     */
    private String paymentName;

    /**
     * Enabled status
     */
    private Integer status;
}
