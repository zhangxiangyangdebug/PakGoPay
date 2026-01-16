package com.pakgopay.data.reqeust.account;

import com.pakgopay.data.reqeust.BaseRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AccountAddRequest extends BaseRequest {

    /** agent name */
    @NotBlank(message = "name is empty")
    private String name;

    /** Wallet addr */
    @NotBlank(message = "walletAddr is empty")
    private String walletAddr;

    /** Wallet name */
    @NotBlank(message = "walletAddr is empty")
    private String walletName;

    /** status */
    @NotNull(message = "status is null")
    private Integer status;

    /** Remark */
    private String remark;
}

