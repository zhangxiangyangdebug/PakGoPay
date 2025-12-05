package com.pakgopay.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class User {
    private Integer userId;

    private String userName;
    private String password;
    private String secretKey;
    private String lastLoginTime;
    private String roleId;
    private String roleName;
}
