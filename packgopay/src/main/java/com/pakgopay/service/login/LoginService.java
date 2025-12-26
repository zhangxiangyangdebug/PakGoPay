package com.pakgopay.service.login;

import com.pakgopay.common.reqeust.LoginRequest;
import com.pakgopay.common.response.CommonResponse;
import jakarta.servlet.http.HttpServletRequest;

public interface LoginService {

    public CommonResponse login(LoginRequest loginRequest);

    public CommonResponse logout(HttpServletRequest loginRequest);

    public CommonResponse getQrCode(String userName, String password);

    public CommonResponse refreshToken(String refreshToken);
}
