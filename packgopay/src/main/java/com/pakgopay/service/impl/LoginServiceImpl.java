package com.pakgopay.service.impl;

import com.pakgopay.common.errorCode.ResultCode;
import com.pakgopay.common.reqeust.LoginRequest;
import com.pakgopay.common.response.CommonResponse;
import com.pakgopay.entity.User;
import com.pakgopay.mapper.UserMapper;
import com.pakgopay.service.LoginService;
import com.pakgopay.thirdUtil.GoogleUtil;
import com.pakgopay.util.TokenUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.List;

@Service
public class LoginServiceImpl implements LoginService {

    @Autowired
    UserMapper userMapper;

    @Override
    public CommonResponse login(LoginRequest loginRequest) {
        String username = loginRequest.getUserName();
        String password = loginRequest.getPassword();

        User oneUser = userMapper.getOneUser(username, password);
        if (!ObjectUtils.isEmpty(oneUser)) {
            if (GoogleUtil.verifyQrCode(oneUser.getSecretKey(), loginRequest.getCode())) {
                return new CommonResponse(0,"login success",TokenUtils.getToken(username)+"&&"+oneUser.getUserName());
            }
            return new CommonResponse(ResultCode.CODE_IS_EXPIRE);
        }
        return new CommonResponse(ResultCode.USER_LOGIN_FAIL);
    }

    @Override
    public CommonResponse getQrCode(String username) {
        User userInfo = userMapper.getSecretKey(username);
        if (ObjectUtils.isEmpty(userInfo)) {
            return new CommonResponse(ResultCode.USER_IS_NOT_EXIST);
        }
        String localSecretKey = userInfo.getSecretKey();
        if (localSecretKey == null) {
            localSecretKey = GoogleUtil.getSecretKey();
            // 往数据库存入该用户的密钥
            int result = userMapper.bingSecretKey(localSecretKey, username);
            if (result == 0) {
                return new CommonResponse(ResultCode.BIND_SECRET_KEY_FAIL);
            }
        }
        String secretKey = localSecretKey == null ? GoogleUtil.getSecretKey() : localSecretKey;

        String qrCode = GoogleUtil.getQrCode(secretKey);
        return CommonResponse.success(qrCode);
    }
}
