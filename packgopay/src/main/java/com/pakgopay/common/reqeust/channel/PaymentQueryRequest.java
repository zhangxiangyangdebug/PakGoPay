package com.pakgopay.common.reqeust.channel;

import com.pakgopay.common.reqeust.ExportBaseRequest;
import lombok.Data;

@Data
public class PaymentQueryRequest extends ExportBaseRequest {
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
}
