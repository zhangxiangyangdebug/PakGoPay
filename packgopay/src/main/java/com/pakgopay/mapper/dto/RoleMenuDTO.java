package com.pakgopay.mapper.dto;

import lombok.Data;

@Data
public class RoleMenuDTO {
    private Integer roleId;

    private String roleName;

    private String menuId;

    private Long createTime;

    private Long updateTime;

    private String creator;

    private String updator;

    private String remark;
}
