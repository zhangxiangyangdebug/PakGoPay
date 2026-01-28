package com.pakgopay.mapper.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class UserDTO implements Serializable {

    private String userId;
    private String loginName;
    private String contactName;
    private String contactEmail;
    private String contactPhone;
    private String loginSecret;
    private Long loginSecretExpire;
    private String apiSecret;
    private Long apiSecretExpire;
    private String password;
    private String lastLoginIP;
    private Long lastLoginTime;
    private Long createTime;
    private String createdBy;
    private Long updatedTime;
    private String updatedBy;
    private String remark;
    private String icon;
    private Integer roleId;
    private String roleName;
    private Integer status;
    private String loginIps;

}
