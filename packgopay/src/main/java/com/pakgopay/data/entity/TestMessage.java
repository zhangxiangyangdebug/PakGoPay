package com.pakgopay.data.entity;

import lombok.Data;

import java.io.Serializable;

@Data
public class TestMessage implements Serializable {

    private static final long serialVersionUID = 123456789L;

    private String content;
}
