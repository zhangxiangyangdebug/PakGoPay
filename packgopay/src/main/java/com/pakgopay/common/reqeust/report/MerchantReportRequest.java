package com.pakgopay.common.reqeust.report;

import lombok.Data;

import java.io.Serializable;

@Data
public class MerchantReportRequest extends BaseReportRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * merchant name
     */
    private String merchantName;
}
