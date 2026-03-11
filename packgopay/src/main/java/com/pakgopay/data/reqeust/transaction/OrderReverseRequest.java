package com.pakgopay.data.reqeust.transaction;

import com.pakgopay.data.reqeust.BaseRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class OrderReverseRequest extends BaseRequest {

    /**
     * Platform order no.
     */
    @NotBlank(message = "transactionNo is empty")
    private String transactionNo;

    /**
     * 0 = collection, 1 = payout.
     */
    @NotNull(message = "bizType is empty")
    @Min(value = 0, message = "bizType must be 0 or 1")
    @Max(value = 1, message = "bizType must be 0 or 1")
    private Integer bizType;

    /**
     * Reverse reason.
     */
    private String remark;
}

