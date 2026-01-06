package com.pakgopay.mapper.dto;

import lombok.Data;

@Data
public class Role {
    private Integer roleId;
    private String roleName;
    private String remark;
    private Long createTime;
    private Long updateTime;
}
