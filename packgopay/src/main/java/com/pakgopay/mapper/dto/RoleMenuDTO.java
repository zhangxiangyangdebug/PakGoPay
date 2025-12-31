package com.pakgopay.mapper.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RoleMenuDTO {
    private Integer roleId;

    private String roleName;

    private String menuId;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private String creator;

    private String updator;

    private String remark;
}
