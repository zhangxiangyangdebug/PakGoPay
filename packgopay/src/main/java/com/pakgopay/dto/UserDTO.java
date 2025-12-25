package com.pakgopay.dto;

import jakarta.persistence.Id;
import lombok.Data;

@Data
public class UserDTO {
    @Id
    private Long id;
    private String userId;

    private String userName;
    private String password;
    private String secretKey;
    private String lastLoginTime;
    private String roleId;
    private String roleName;
    private Integer status;
}
