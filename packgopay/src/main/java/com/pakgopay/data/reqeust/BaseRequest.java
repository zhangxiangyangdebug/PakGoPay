package com.pakgopay.data.reqeust;

import lombok.Data;

@Data
public class BaseRequest {
    /**
     * user Id
     */
    String userId;

    /**
     * user name
     */
    String userName;

    /**
     * client Ip
     */
    String clientIp;


    Long googleCode;
}
