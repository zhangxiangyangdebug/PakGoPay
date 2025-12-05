package com.pakgopay.service;

import com.pakgopay.common.reqeust.LoginRequest;
import com.pakgopay.common.response.CommonResponse;

public interface LoginService {

    public CommonResponse login(LoginRequest loginRequest);

    public CommonResponse getQrCode(Integer userId, String password);

    public CommonResponse refreshToken(String refreshToken);
}
