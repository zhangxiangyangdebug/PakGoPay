package com.pakgopay.service.impl;

import com.google.gson.Gson;
import com.pakgopay.common.enums.CacheKeys;
import com.pakgopay.common.enums.ResultCode;
import com.pakgopay.common.reqeust.LoginRequest;
import com.pakgopay.common.response.CommonResponse;
import com.pakgopay.common.response.LoginResponse;
import com.pakgopay.entity.User;
import com.pakgopay.mapper.UserMapper;
import com.pakgopay.service.AuthorizationService;
import com.pakgopay.service.LoginService;
import com.pakgopay.thirdUtil.GoogleUtil;
import com.pakgopay.thirdUtil.RedisUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.Date;

@Service
public class LoginServiceImpl implements LoginService {

    @Autowired
    UserMapper userMapper;

    @Autowired
    RedisUtil redisUtil;

    @Autowired
    private AuthorizationService authorizationService;

    @Override
    public CommonResponse login(LoginRequest loginRequest) {
        String userId = loginRequest.getUserName();
        String password = loginRequest.getPassword();
        String value = redisUtil.getValue(CacheKeys.USER_INFO_KEY_PREFIX + userId);
        User user = null;
        if (ObjectUtils.isEmpty(value)) {
            // 缓存没有用户数据，从数据库获取
            user = userMapper.getOneUser(userId, password);
            System.out.println(user.toString());
        } else {
            //
            user = new Gson().fromJson(value, User.class);
        }
        // 对象依旧为空，则用户不存在
        if (ObjectUtils.isEmpty(user)) {
            return CommonResponse.fail(ResultCode.USER_IS_NOT_EXIST);
        }

        // 检验谷歌令牌验证码
        if (GoogleUtil.verifyQrCode(user.getSecretKey(), loginRequest.getCode())) {
            // 登陆成功
            //String token = TokenUtils.generateToken(userId);
            //String refreshToken = TokenUtils.generateToken(userId);
            String userName = user.getUserName();
            String token = authorizationService.createAccessIdToken(userId,userName);
            String refreshToken = authorizationService.createRefreshToken(userId, userName);
            // 缓存当前登陆用户 refreshToken 创建的起始时间， 用于刷新token时判断是否需要重新生成refreshToken
            redisUtil.setWithSecondExpire(CacheKeys.REFRESH_TOKEN_START_TIME_PREFIX + userId, String.valueOf(System.currentTimeMillis()), (int)AuthorizationService.refreshTokenExpirationTime);
            // 更新用户最近登陆时间
            user.setLastLoginTime(new Date().toLocaleString());
            try {
                userMapper.setLastLoginTime(new Date().toLocaleString(), userId);
            } catch (Exception e) {
                // 更新数据库失败
                return CommonResponse.fail(ResultCode.INTERNAL_SERVER_ERROR);
            }
            // 缓存用户信息 移除多余的信息
            user.setPassword(null);
            redisUtil.set(CacheKeys.USER_INFO_KEY_PREFIX + userId, new Gson().toJson(user));
            LoginResponse loginResponse = new LoginResponse();
            loginResponse.setCode(ResultCode.SUCCESS.getCode());
            loginResponse.setMessage("login success");
            loginResponse.setToken(token);
            loginResponse.setRefreshToken(refreshToken);
            loginResponse.setUserName(user.getUserName());
            loginResponse.setUserId(userId);
            return loginResponse;
        }
        return new CommonResponse(ResultCode.CODE_IS_EXPIRE);
    }

    @Override
    public CommonResponse getQrCode(Integer userId, String password) {
        User userInfo = userMapper.getSecretKey(userId, password);
        if (ObjectUtils.isEmpty(userInfo)) {
            return CommonResponse.fail(ResultCode.USER_VERIFY_FAIL, "check user info failed");
        }
        String localSecretKey = userInfo.getSecretKey();
        if (localSecretKey == null) {
            localSecretKey = GoogleUtil.getSecretKey();
            // 往数据库存入该用户的密钥
            int result = userMapper.bingSecretKey(localSecretKey, userId);
            if (result == 0) {
                return new CommonResponse(ResultCode.BIND_SECRET_KEY_FAIL);
            }
        }
        String secretKey = localSecretKey == null ? GoogleUtil.getSecretKey() : localSecretKey;

        String qrCode = GoogleUtil.getQrCode(secretKey);
        return CommonResponse.success(qrCode);
    }

    @Override
    public CommonResponse refreshToken(String refreshToken) {
        // 从refreshToken中获取用户账号
        String userInfos = AuthorizationService.verifyToken(refreshToken);
        if (userInfos == null) {
            // RT过期，前端需要重新跳转登陆页面让用户重新登陆
            return new CommonResponse(ResultCode.REFRESH_TOKEN_EXPIRE);
        }
        String account = userInfos.split("&")[0];
        String userName = userInfos.split("&")[1];
        // 创建新的token
        String accessToken = authorizationService.createAccessIdToken(account, userName);
        // 判断refreshToken是否要过期 即将过期则刷新RT
        long minTimeOfRefreshToken =  2*AuthorizationService.accessTokenExpirationTime; //refreshToken剩余时常保证在token有效期的2倍以上，否则刷新RT
        Long refreshTokenStartTime = redisUtil.getValue(CacheKeys.REFRESH_TOKEN_START_TIME_PREFIX+account) == null ? null : Long.parseLong(redisUtil.getValue(CacheKeys.REFRESH_TOKEN_START_TIME_PREFIX+account));
        if (refreshTokenStartTime == null || (refreshTokenStartTime + AuthorizationService.refreshTokenExpirationTime*1000)- System.currentTimeMillis() <= minTimeOfRefreshToken*1000) {
            // 刷新refreshToken
            refreshToken = authorizationService.createRefreshToken(account, userName);
            redisUtil.setWithSecondExpire(CacheKeys.REFRESH_TOKEN_START_TIME_PREFIX+account, String.valueOf(System.currentTimeMillis()), (int)AuthorizationService.refreshTokenExpirationTime);
        }
        LoginResponse loginResponse = new LoginResponse();
        loginResponse.setCode(ResultCode.SUCCESS.getCode());
        loginResponse.setMessage("login success");
        loginResponse.setToken(accessToken);
        loginResponse.setRefreshToken(refreshToken);
        loginResponse.setUserName(userName);
        loginResponse.setUserId(account);
        return loginResponse;
    }
}
