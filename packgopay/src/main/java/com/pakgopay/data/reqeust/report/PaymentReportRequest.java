package com.pakgopay.data.reqeust.report;

import lombok.Data;

@Data
public class PaymentReportRequest extends BaseReportRequest{
    /** Optional: payment Id (fuzzy match) */
    private String paymentId;
}
