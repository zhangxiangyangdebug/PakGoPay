package com.pakgopay.data.reqeust;

import lombok.Data;

import java.io.Serializable;

@Data
public class LoginRequest implements Serializable {
    private static final long serialVersionUID = 1L;
    private boolean remember;

    private Integer userId;

    private String userName;

    private String password;

    private Long code;

    private String turnstileToken;
}
