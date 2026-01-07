package com.pakgopay.entity;

import com.pakgopay.common.entity.BaseReportEntity;
import lombok.Data;

@Data
public class MerchantReportEntity extends BaseReportEntity {

    /** Optional: merchant user ID */
    private String userId;

    /** Optional: merchant name (fuzzy match) */
    private String merchantName;

    /** Optional: order type */
    private Integer orderType;
}
