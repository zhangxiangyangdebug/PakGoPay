package com.pakgopay.data.reqeust.systemConfig;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.io.Serializable;

@Data
public class EditUserRequest implements Serializable {

    @NotBlank(message = "userId is empty")
    private String userId;

    @NotBlank(message = "loginName is empty")
    private String loginName;

    private String password;

    private String confirmPassword;

    @NotNull(message = "roleId is empty")
    private Integer roleId;

    @NotNull(message = "status is empty")
    private Integer status;

    @NotNull(message = "googleCode is empty")
    private Integer googleCode;

    @NotBlank(message = "loginIps is empty")
    private String loginIps;

    private String withdrawalIps;

    @NotBlank(message = "contactName is empty")
    private String contactName;

    @NotBlank(message = "contactEmail is empty")
    @Email(message = "contactEmail format error")
    private String contactEmail;

    @NotBlank(message = "contactPhone is empty")
    @Pattern(regexp = "^[0-9+\\-]{6,20}$", message = "contactPhone format error")
    private String contactPhone;

    private String operatorId;
}
