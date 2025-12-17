package com.pakgopay.common.enums;

public enum ResultCode {
    SUCCESS(0, "success"),
    FAIL(1, "fail"),
    INVALID_TOKEN(1001, "token is invalid"),
    TOKEN_IS_EXPIRE(1002, "token is expire"),
    USER_LOGIN_FAIL(1003, "user login fail"),
    USER_IS_NOT_EXIST(1004, "user is not exist"),
    INTERNAL_SERVER_ERROR(1005, "Internal Server Error"),
    BIND_SECRET_KEY_FAIL(1006, "bind secret key fail"),
    CODE_IS_EXPIRE(1007, "code is expire"),
    REFRESH_TOKEN_EXPIRE(1008, "refresh token expire"),
    USER_PASSWORD_ERROR(1009, "user password error"),
    USER_VERIFY_FAIL(10010, "user verify fail"),
    NO_ROLE_INFO_FOUND(10011, "no role info found for this user!");

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
