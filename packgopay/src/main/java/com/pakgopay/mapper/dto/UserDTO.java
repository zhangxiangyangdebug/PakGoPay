package com.pakgopay.mapper.dto;

import jakarta.persistence.Id;
import lombok.Data;

import java.io.Serializable;
import java.sql.Timestamp;
import java.time.LocalDateTime;

@Data
public class UserDTO implements Serializable {

    private String userId;
    private String loginName;
    private String contactName;
    private String contactEmail;
    private String contactPhone;
    private String loginSecret;
    private LocalDateTime loginSecretExpire;
    private String apiSecret;
    private LocalDateTime apiSecretExpire;
    private String password;
    private String lastLoginIP;
    private LocalDateTime lastLoginTime;
    private LocalDateTime createTime;
    private String createdBy;
    private LocalDateTime updatedTime;
    private String updatedBy;
    private String remark;
    private String icon;
    private Integer roleId;
    private Integer status;
}
