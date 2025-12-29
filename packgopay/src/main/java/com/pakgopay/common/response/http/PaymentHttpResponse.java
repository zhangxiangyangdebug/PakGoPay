package com.pakgopay.common.response.http;

import lombok.Data;

@Data
public class PaymentHttpResponse {
    private Integer code; // 响应码
    private String message; // 响应消息
    private String data; // 响应数据
}
