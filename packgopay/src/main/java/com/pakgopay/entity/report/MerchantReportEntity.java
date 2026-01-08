package com.pakgopay.entity.report;

import lombok.Data;

@Data
public class MerchantReportEntity extends BaseReportEntity {

    /** Optional: merchant user ID */
    private String userId;

    /** Optional: merchant name (fuzzy match) */
    private String merchantName;
}
