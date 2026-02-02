package com.pakgopay.service;

import com.pakgopay.data.reqeust.LoginRequest;
import com.pakgopay.data.response.CommonResponse;
import jakarta.servlet.http.HttpServletRequest;

public interface LoginService {

    public CommonResponse login(LoginRequest loginRequest, HttpServletRequest request);

    public CommonResponse logout(HttpServletRequest loginRequest);

    public CommonResponse generateLoginQrCode(String userName, String password);

    public CommonResponse refreshAuthToken(String refreshToken, HttpServletRequest request);
}
