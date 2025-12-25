package com.pakgopay.mapper.dto;

import lombok.Data;

@Data
public class ConfigurationDto {

    /**
     * user id
     */
    String userId;

    /**
     * collection ip white list
     */
    String colWhiteIpList;

    /**
     * pay ip white list
     */
    String payWhiteIpList;

    /**
     * user is enabled status
     */
    Boolean enableStatus;
}
