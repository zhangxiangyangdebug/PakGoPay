package com.pakgopay.common.reqeust.channel;

import com.pakgopay.common.reqeust.BaseRequest;
import lombok.Data;

@Data
public class PaymentRequest extends BaseRequest {
    /**
     * Payment number
     */
    private String paymentId;

    /**
     * Payment name
     */
    private String paymentName;

    /**
     * Enabled status
     */
    private Integer status;

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
