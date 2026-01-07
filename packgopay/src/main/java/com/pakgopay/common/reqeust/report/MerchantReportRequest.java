package com.pakgopay.common.reqeust.report;

import com.pakgopay.common.reqeust.BaseRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

@Data
public class MerchantReportRequest extends BaseRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * merchant name
     */
    private String merchantName;

    /**
     * order type
     */
    @NotNull(message = "orderType is null")
    private Integer orderType;

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
