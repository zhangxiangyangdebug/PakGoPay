package com.pakgopay.data.entity;

import lombok.Data;

@Data
public class Extra {
    private boolean needLogin;
    private String title;
    private String permission;
    private String role;
}
