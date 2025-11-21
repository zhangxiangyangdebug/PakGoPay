package com.pakgopay.common.response;

import com.pakgopay.common.errorCode.ResultCode;
import lombok.Data;

import java.io.Serializable;

@Data
public class CommonResponse implements Serializable {
    private Integer code;
    private String message;
    private String data;

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

    public static CommonResponse success(String data) {
        return new CommonResponse(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), data);
    }
    public static CommonResponse fail(ResultCode resultCode) {
        return new CommonResponse(resultCode);
    }
}
