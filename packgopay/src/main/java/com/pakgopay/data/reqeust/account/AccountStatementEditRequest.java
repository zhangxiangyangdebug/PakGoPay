package com.pakgopay.data.reqeust.account;

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
     * merchant user id
     */
    @NotBlank(message = "merchantAgentId is empty")
    private String merchantAgentId;

    /**
     * is agree?
     */
    private boolean isAgree;

    /**
     * remark
     */
    private String remark;
}
