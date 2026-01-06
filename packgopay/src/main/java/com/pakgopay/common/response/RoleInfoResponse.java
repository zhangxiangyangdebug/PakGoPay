package com.pakgopay.common.response;

import lombok.Data;

@Data
public class RoleInfoResponse {
    private Integer roleId;
    private String roleName;
    private String remark;
    private String createTime;
    private String updateTime;
}
