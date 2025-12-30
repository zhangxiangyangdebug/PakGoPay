package com.pakgopay.common.response.roleManagement;

import lombok.Data;

import java.util.List;

@Data
public class RoleMenuInfoResponse {
    private Integer roleId;
    private String remark;
    private String roleName;
    private List<String> menuList;
}
