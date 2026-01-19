package com.pakgopay.data.reqeust;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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
    private String loginIps;

    /** Contact name */
    @NotBlank(message = "contactName is empty")
    private String contactName;

    /** Contact email */
    @NotBlank(message = "contactEmail is empty")
    @Email(message = "contactEmail format error")
    private String contactEmail;

    /** Contact phone */
    @NotBlank(message = "contactPhone is empty")
    @Pattern(regexp = "^[0-9+\\-]{6,20}$", message = "contactPhone format error")
    private String contactPhone;

    @NotNull
    private String operatorId;

}
