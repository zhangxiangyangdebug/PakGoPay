package com.pakgopay.data.entity;

import lombok.Data;
import org.springframework.stereotype.Component;

import java.io.Serializable;

@Data
public class Message implements Serializable {
    private String id;
    private String userId;
    private String content;
    private long timestamp;
    private boolean read;
    private String path;
    private String title;
    // getters/setters/constructors
}

