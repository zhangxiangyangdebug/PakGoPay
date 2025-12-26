package com.pakgopay.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Data;

@Entity
@Data
public class User {
    @Id
    private Long id;
    private String userId;

    private String LoginName;
    private String password;
    private String secretKey;
    private String lastLoginTime;
    private String roleId;
    private String roleName;
    private Integer status;
}
