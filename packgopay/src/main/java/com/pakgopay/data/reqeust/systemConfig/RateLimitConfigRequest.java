package com.pakgopay.data.reqeust.systemConfig;

import lombok.Data;

@Data
public class RateLimitConfigRequest {
    private Boolean enabled;
    private Long windowSeconds;
    private Long maxRequests;
    private String fixedIpQps;
    private Integer googleCode;
}
