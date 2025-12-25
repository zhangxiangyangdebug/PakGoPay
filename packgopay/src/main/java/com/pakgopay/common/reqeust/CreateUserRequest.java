package com.pakgopay.common.reqeust;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NonNull;

import java.io.Serializable;

@Data
public class CreateUserRequest implements Serializable {

    @NotNull
    private String username;
    @NotNull
    private String password;
    @NotNull
    private String confirmPassword;
    @NotNull
    private String roleId;
    @NotNull
    private Integer userStatus;
    @NotNull
    private Long googleCode;

    @NotNull
    private String operatorId;

}
