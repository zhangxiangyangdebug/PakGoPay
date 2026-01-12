package com.pakgopay.entity.report;

import lombok.Data;

@Data
public class PaymentReportEntity extends BaseReportEntity {

    /** Optional: payment Id (fuzzy match) */
    private Long paymentId;
}
