package com.pakgopay.data.reqeust;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

@Data
public class CreateUserRequest implements Serializable {

    @NotNull
    private String loginName;
    @NotNull
    private String password;
    @NotNull
    private String confirmPassword;
    @NotNull
    private Integer roleId;
    @NotNull
    private Integer status;
    @NotNull
    private Integer googleCode;

    @NotNull
    private String operatorId;

}
