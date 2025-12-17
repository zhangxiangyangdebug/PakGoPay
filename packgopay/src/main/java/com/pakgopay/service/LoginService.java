package com.pakgopay.service;

import com.pakgopay.common.reqeust.LoginRequest;
import com.pakgopay.common.response.CommonResponse;

import javax.servlet.http.HttpServletRequest;

public interface LoginService {

    public CommonResponse login(LoginRequest loginRequest);

    public CommonResponse logout(HttpServletRequest loginRequest);

    public CommonResponse getQrCode(String userId, String password);

    public CommonResponse refreshToken(String refreshToken);
}
