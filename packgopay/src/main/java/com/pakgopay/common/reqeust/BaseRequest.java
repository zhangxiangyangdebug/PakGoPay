package com.pakgopay.common.reqeust;

import lombok.Data;

@Data
public class BaseRequest {
    /**
     * user Id
     */
    String userId;

    /**
     * client Ip
     */
    String clientIp;
}
