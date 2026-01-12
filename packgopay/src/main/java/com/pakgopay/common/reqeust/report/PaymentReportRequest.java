package com.pakgopay.common.reqeust.report;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PaymentReportRequest extends BaseReportRequest{
    /** Optional: payment Id (fuzzy match) */
    @NotNull(message = "paymentId is null")
    private String paymentId;
}
