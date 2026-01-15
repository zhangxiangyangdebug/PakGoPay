package com.pakgopay.common.reqeust.agent;

import com.pakgopay.common.reqeust.BaseRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AgentAccountAddRequest extends BaseRequest {

    /** agent name */
    @NotBlank(message = "agentName is empty")
    private String agentName;

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

