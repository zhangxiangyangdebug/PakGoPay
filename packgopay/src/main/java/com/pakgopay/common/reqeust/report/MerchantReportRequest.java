package com.pakgopay.common.reqeust.report;

import com.pakgopay.common.reqeust.BaseRequest;
import lombok.Data;

@Data
public class MerchantReportRequest extends BaseRequest {

    /**
     * merchant name
     */
    private String merchantName;

    /**
     * 查询开始时间
     */
    private String startTime;

    /**
     * 查询结束时间
     */
    private String endTime;

}
