package com.pakgopay.common.response;

import com.alibaba.fastjson.JSON;
import com.pakgopay.common.enums.ResultCode;
import lombok.Data;

import java.io.Serializable;

@Data
public class CommonResponse<T> implements Serializable {
    private Integer code;
    private String message;
    private String data;

    public CommonResponse() {
    }

    public CommonResponse(Integer code, String message) {
        this.code = code;
        this.message = message;
    }

    public CommonResponse(Integer code, String message, String data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public CommonResponse(String message, String data) {

        this.message = message;
        this.data = data;
        this.code = 0;
    }

    public CommonResponse(ResultCode resultCode) {
        this.code = resultCode.getCode();
        this.message = resultCode.getMessage();
    }

    public static <T> CommonResponse<T> success(String data) {
        return new CommonResponse<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), data);
    }

    public static <T> CommonResponse<T> success(Object data) {
        return new CommonResponse<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), JSON.toJSONString(data));
    }

    public static CommonResponse<Void> fail(ResultCode resultCode) {
        return new CommonResponse<>(resultCode);
    }

    public static CommonResponse<Void> fail(ResultCode resultCode, String message) {
        return new CommonResponse<>(resultCode.getCode(), message);
    }
}
