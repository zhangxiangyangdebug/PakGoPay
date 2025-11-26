package com.pakgopay.common.errorCode;

public enum ResultCode {
    SUCCESS(0, "success"),
    INVALID_TOKEN(1001, "token is invalid"),
    TOKEN_IS_EXPIRE(1002, "token is expire"),
    USER_LOGIN_FAIL(1003, "user login fail"),
    USER_IS_NOT_EXIST(1004, "user is not exist"),
    INTERNAL_SERVER_ERROR(1005, "Internal Server Error"),
    BIND_SECRET_KEY_FAIL(1006, "bind secret key fail"),
    CODE_IS_EXPIRE(1007, "code is expire");

    private final Integer code;
    private final String message;
    ResultCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }

    public Integer getCode() {
        return code;
    }
    public String getMessage() {
        return message;
    }

}
