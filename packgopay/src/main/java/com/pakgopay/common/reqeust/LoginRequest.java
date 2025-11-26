package com.pakgopay.common.reqeust;

import lombok.Data;
import reactor.util.annotation.NonNull;

import java.io.Serializable;

@Data
public class LoginRequest implements Serializable {
    private static final long serialVersionUID = 1L;
    private boolean remember;

    private String userName;

    private String password;

    private Long code;
}
