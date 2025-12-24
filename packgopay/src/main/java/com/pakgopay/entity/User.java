package com.pakgopay.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Data;

@Entity
@Data
public class User {
    @Id
    private Long id;
    private Integer userId;

    private String userName;
    private String password;
    private String secretKey;
    private String lastLoginTime;
    private String roleId;
    private String roleName;
}
