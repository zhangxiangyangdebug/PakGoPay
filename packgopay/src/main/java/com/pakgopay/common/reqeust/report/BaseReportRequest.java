package com.pakgopay.common.reqeust.report;

import com.pakgopay.common.reqeust.ExportBaseRequest;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class BaseReportRequest extends ExportBaseRequest {

    /** Optional: order type */
    private Integer orderType;

    /**
     * is need page card data
     */
    private Boolean isNeedCardData = false;

    /**
     * is need page card data
     */
    @NotBlank(message = "currency is empty")
    private String currency;

    /**
     * search start time
     */
    @NotBlank(message = "startTime is empty")
    private String startTime;

    /**
     * search end time
     */
    @NotBlank(message = "endTime is empty")
    private String endTime;
}
