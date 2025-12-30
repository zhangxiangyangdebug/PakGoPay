package com.pakgopay.common.reqeust.roleManagement;

import lombok.Data;

import java.util.List;

@Data
public class AddRoleRequest {
    private String roleName;
    private String remark;
    private Long googleCode;
    private List<String> menuList;
}
