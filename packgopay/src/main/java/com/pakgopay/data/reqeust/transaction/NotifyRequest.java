package com.pakgopay.data.reqeust.transaction;

import com.pakgopay.data.reqeust.BaseRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class NotifyRequest extends BaseRequest {
    private String transactionNo;
    private String merchantNo;
    private String status;
    private String remark;
}
