package com.pakgopay.common.reqeust.report;

import com.pakgopay.common.reqeust.BaseRequest;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class BaseReportRequest extends BaseRequest {
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

    /**
     * Page number (start from 1)
     */
    private Integer pageNo;

    /**
     * Page size
     */
    private Integer pageSize;

    /**
     * default page size 10
     * @return page size
     */
    public Integer getPageSize() {
        if (pageSize == null) {
            return 10;
        }
        return pageSize;
    }

    /**
     * default page no 1
     * @return page no
     */
    public Integer getPageNo() {
        if (pageNo == null) {
            return 1;
        }
        return pageNo;
    }
}
