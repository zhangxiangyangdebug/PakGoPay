package com.pakgopay.data.entity;

import lombok.Data;

import java.io.Serializable;

@Data
public class Message implements Serializable {
    private String id;
    private String userId;
    private String content;
    private long timestamp;
    private boolean read;

    // getters/setters/constructors
}

