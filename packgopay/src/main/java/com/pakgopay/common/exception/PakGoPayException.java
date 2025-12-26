package com.pakgopay.common.exception;

import com.pakgopay.common.enums.ResultCode;
import lombok.Getter;

@Getter
public class PakGoPayException extends Exception {

    private ResultCode code;
    private String message;

    public PakGoPayException(ResultCode resultCode, String message) {
        super(message);
        this.code = resultCode;
    }

    public PakGoPayException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode;
        this.message = resultCode.getMessage();
    }
}
