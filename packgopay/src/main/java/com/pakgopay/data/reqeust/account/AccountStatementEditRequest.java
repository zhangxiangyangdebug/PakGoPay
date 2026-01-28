package com.pakgopay.data.reqeust.account;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.pakgopay.data.reqeust.BaseRequest;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AccountStatementEditRequest extends BaseRequest  {

    /**
     * order Id
     */
    @NotBlank(message = "id is empty")
    private String id;

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
