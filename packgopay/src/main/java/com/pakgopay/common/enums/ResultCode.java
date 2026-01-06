package com.pakgopay.common.enums;

import lombok.Getter;

@Getter
public enum ResultCode {
    SUCCESS(0, "success"),
    FAIL(1, "fail"),

    // common code start with 1001
    INVALID_TOKEN(100101, "token is invalid"),
    TOKEN_IS_EXPIRE(100102, "token is expire"),
    USER_LOGIN_FAIL(100103, "user login fail"),
    USER_IS_NOT_EXIST(100104, "user is not exist"),
    INTERNAL_SERVER_ERROR(100105, "Internal Server Error"),
    BIND_SECRET_KEY_FAIL(100106, "bind secret key fail"),
    CODE_IS_EXPIRE(100107, "code is expire"),
    REFRESH_TOKEN_EXPIRE(100108, "refresh token expire"),
    USER_PASSWORD_ERROR(100109, "user password error"),
    USER_VERIFY_FAIL(100110, "user verify fail"),
    NO_ROLE_INFO_FOUND(100111, "no role info found for this user!"),
    USER_IS_NOT_IN_USE(100112,"user is not in use"),
    REQUEST_TIME_OUT(100113, "request time out"),
    HTTP_REQUEST_FAILED(100113, "http request failed"),
    DATA_BASE_ERROR(100114, "execute sql failed"),
    INVALID_PARAMS(100115, "params is invalid"),

    // order code start with 1002
    ORDER_PARAM_VALID(100201, "request param is valid"),
    IS_NOT_WHITE_IP(100202, "client ip is not in white list"),
    USER_NOT_ENABLE(100203,"user is not enabled"),
    MERCHANT_CODE_IS_EXISTS(100204,"merchant order id is exits"),
    MERCHANT_HAS_NO_AVAILABLE_CHANNEL(100205,"the merchant has no available channels."),
    ORDER_AMOUNT_OVER_LIMIT(100206,"the order over amount limit."),
    PAYMENT_AMOUNT_OVER_LIMIT(100207,"exceeding the daily/monthly available limit for the channel"),
    PAYMENT_NOT_SUPPORT_CURRENCY(100208,"channel is not support this currency"),
    MERCHANT_NOT_SUPPORT_COLLECTION(100209, "merchant is not support collection"),
    MERCHANT_NOT_SUPPORT_PAYOUT(100210, "merchant is not support payout"),
    MERCHANT_ORDER_NO_NOT_EXISTS(100211, "merchantOrderNo is not exits"),
    MERCHANT_HAS_NO_BALANCE_DATA(100212, "user has no balance data"),
    MERCHANT_BALANCE_NOT_ENOUGH(100213, "merchants with insufficient available balance");

    private final Integer code;
    private final String message;
    ResultCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }

}
