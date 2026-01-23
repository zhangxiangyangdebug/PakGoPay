package com.pakgopay.data.response;


import com.pakgopay.common.enums.ResultCode;
import lombok.Data;

@Data
public class LoginResponse extends CommonResponse {
    private String token;
    private String refreshToken;
    private String userName;
    private String userId;
    private String roleName;

    public LoginResponse() {}

    public LoginResponse(Integer code, String message, String data) {
        super(code, message, data);
    }

    public LoginResponse(String message, String data) {
        super(message, data);
    }

    public LoginResponse(ResultCode resultCode) {
        super(resultCode);
    }
}
