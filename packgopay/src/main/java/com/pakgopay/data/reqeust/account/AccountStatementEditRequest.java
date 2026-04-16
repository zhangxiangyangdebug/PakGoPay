package com.pakgopay.data.reqeust.account;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.pakgopay.data.reqeust.BaseRequest;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AccountStatementEditRequest extends BaseRequest  {

    /**
     * statement serial no
     */
    @NotBlank(message = "serialNo is empty")
    private String serialNo;

    /**
     * is agree?
     */
    @JsonProperty("isAgree")
    private boolean isAgree;

    /**
     * remark
     */
    private String remark;
}
