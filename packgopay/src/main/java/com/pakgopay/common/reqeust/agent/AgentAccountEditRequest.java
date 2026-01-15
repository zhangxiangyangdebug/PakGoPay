package com.pakgopay.common.reqeust.agent;

import com.pakgopay.common.reqeust.BaseRequest;
import lombok.Data;

@Data
public class AgentAccountEditRequest extends BaseRequest {

    /**
     * id
     */
    private String id;

    /**
     * account name
     */
    private String walletAddr;

    /**
     * status
     */
    private Integer status;
}
