package com.pakgopay.service;

import com.pakgopay.data.reqeust.LoginRequest;
import com.pakgopay.data.response.CommonResponse;
import jakarta.servlet.http.HttpServletRequest;

public interface LoginService {

    public CommonResponse login(LoginRequest loginRequest);

    public CommonResponse logout(HttpServletRequest loginRequest);

    public CommonResponse getQrCode(String userName, String password);

    public CommonResponse refreshToken(String refreshToken);
}
