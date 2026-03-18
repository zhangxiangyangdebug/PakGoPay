package com.pakgopay.data.reqeust.bankCode;

import com.pakgopay.data.reqeust.BaseRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class BankCodeQueryRequest extends BaseRequest {
    private String bankName;
    private String bankCode;
    private String currencyCode;

    @NotNull(message = "pageNo is null")
    @Min(value = 1, message = "pageNo must be greater than 0")
    private Integer pageNo = 1;

    @NotNull(message = "pageSize is null")
    @Min(value = 1, message = "pageSize must be greater than 0")
    @Max(value = 200, message = "pageSize cannot exceed 200")
    private Integer pageSize = 10;

    public Integer getOffSet() {
        return (pageNo - 1) * pageSize;
    }
}
