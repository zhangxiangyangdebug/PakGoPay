package com.pakgopay.data.reqeust.account;

import com.pakgopay.data.reqeust.BaseRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class WithdrawOrderAuditRequest extends BaseRequest {

    /**
     * Withdraw order number
     */
    @NotBlank(message = "withdrawNo is empty")
    private String withdrawNo;

    /**
     * Audit status: 1.rejected 2.success
     */
    @NotNull(message = "status is null")
    private Integer status;

    /**
     * Fail reason
     */
    private String failReason;

    /**
     * Remark
     */
    private String remark;
}
