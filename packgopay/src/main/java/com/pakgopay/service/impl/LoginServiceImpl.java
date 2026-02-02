package com.pakgopay.service.impl;

import com.alibaba.fastjson.JSON;
import com.google.gson.Gson;
import com.pakgopay.common.constant.CommonConstant;
import com.pakgopay.common.enums.ResultCode;
import com.pakgopay.data.reqeust.LoginRequest;
import com.pakgopay.data.response.CommonResponse;
import com.pakgopay.data.response.LoginResponse;
import com.pakgopay.mapper.UserMapper;
import com.pakgopay.mapper.dto.UserDTO;
import com.pakgopay.service.common.AuthorizationService;
import com.pakgopay.service.LoginService;
import com.pakgopay.thirdUtil.GoogleUtil;
import com.pakgopay.thirdUtil.RedisUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.time.Instant;

@Service
public class LoginServiceImpl implements LoginService {

    @Autowired
    UserMapper userMapper;

    @Autowired
    RedisUtil redisUtil;

    @Autowired
    private AuthorizationService authorizationService;

    @Override
    public CommonResponse login(LoginRequest loginRequest, HttpServletRequest request) {
        String ip = request.getParameter(CommonConstant.ATTR_IP);
        String userAgent = request.getParameter(CommonConstant.ATTR_USERAGENT);
        String userName = loginRequest.getUserName();
        String password = loginRequest.getPassword();
        String value = redisUtil.getValue(CommonConstant.USER_INFO_KEY_PREFIX + userName);
        UserDTO user = null;
        if (ObjectUtils.isEmpty(value)) {
            // 缓存没有用户数据，从数据库获取
            user = userMapper.getOneUser(userName);
        } else {
            //
            user = new Gson().fromJson(value, UserDTO.class);
        }
        // 对象依旧为空，则用户不存在
        if (ObjectUtils.isEmpty(user)) {
            return CommonResponse.fail(ResultCode.USER_IS_NOT_EXIST);
        }

        // 用户状态是否启用
        if (user.getStatus() == 0) {
            return CommonResponse.fail(ResultCode.USER_IS_NOT_IN_USE, "user has been locked");
        }

        if (!user.getPassword().equals(password)) {
            String passwordErrorTimes = redisUtil.getValue(CommonConstant.USER_PASSWORD_ERROR_TIMES + userName);
            int errorTimes = 0;
            if (passwordErrorTimes != null) {
                errorTimes = Integer.parseInt(passwordErrorTimes);
            }
            int currentTimes = errorTimes + 1;
            redisUtil.set(CommonConstant.USER_PASSWORD_ERROR_TIMES + userName, String.valueOf(currentTimes));
            if (errorTimes >= 3) {
                userMapper.stopLoginUser(user.getUserId(), 0);
                // 锁定用户后，移除所有计数，后续解禁用户即可正常使用
                redisUtil.remove(CommonConstant.USER_PASSWORD_ERROR_TIMES + userName);
                return CommonResponse.fail(ResultCode.USER_LOGIN_FAIL, "you have input error password more than 3 times, account has been locked!");
            }


            return CommonResponse.fail(ResultCode.USER_PASSWORD_ERROR);
        }


        // 用户未设置谷歌令牌 限制登陆3次，3次后更改状态为禁用
        //UserDTO secretKeyInfo = userMapper.getSecretKey(user.getUserId(), user.getPassword());
        String secretKey = user.getLoginSecret();
        if (ObjectUtils.isEmpty(secretKey)) {
            String noKeyTimes = redisUtil.getValue(CommonConstant.USER_NO_KEY_LOGIN_TIMES + userName);
            int times = noKeyTimes != null ? Integer.parseInt(noKeyTimes) : 0;

            if (times + 1 >= 3) {
                int result = userMapper.stopLoginUser(user.getUserId(), 0);
                if (result <= 0) {
                    return CommonResponse.fail(ResultCode.USER_LOGIN_FAIL);
                }
                if (times >= 5) {
                    redisUtil.remove(CommonConstant.USER_NO_KEY_LOGIN_TIMES + userName);
                }
            } else {
                redisUtil.set(CommonConstant.USER_NO_KEY_LOGIN_TIMES + userName, String.valueOf(times + 1));
            }

            return loginSuccess(user, userName, ip, userAgent);
        } else {
            // 检验谷歌令牌验证码
            if (loginRequest.getCode() == null) {
                return CommonResponse.fail(ResultCode.USER_LOGIN_FAIL, "code is null");
            }
            if (GoogleUtil.verifyQrCode(user.getLoginSecret(), loginRequest.getCode())) {
                // 登陆成功
                //String token = TokenUtils.generateToken(userId);
                //String refreshToken = TokenUtils.generateToken(userId);
                /*String userName = user.getUserName();*/
                return loginSuccess(user, userName, ip, userAgent);
            }
        }
        return new CommonResponse(ResultCode.CODE_IS_EXPIRE);
    }

    private CommonResponse loginSuccess(UserDTO user, String userName, String ip, String userAgent) {
        String userId = user.getUserId();
        String token = authorizationService.createAccessIdToken(user.getUserId(), userName, ip, userAgent);
        String refreshToken = authorizationService.createRefreshToken(userId, userName, ip, userAgent);
        // 缓存当前登陆用户 refreshToken 创建的起始时间， 用于刷新token时判断是否需要重新生成refreshToken
        redisUtil.setWithSecondExpire(CommonConstant.REFRESH_TOKEN_START_TIME_PREFIX + userId, String.valueOf(System.currentTimeMillis()), (int) AuthorizationService.refreshTokenExpirationTime);
        // 更新用户最近登陆时间
        Long now = Instant.now().getEpochSecond();
        try {
            userMapper.setLastLoginTime(now, userId);
        } catch (Exception e) {
            // 更新数据库失败
            return CommonResponse.fail(ResultCode.INTERNAL_SERVER_ERROR);
        }
        user.setLastLoginTime(null);
        // 缓存用户信息 移除多余的信息
        redisUtil.set(CommonConstant.USER_INFO_KEY_PREFIX + userId, JSON.toJSONString(user));
        LoginResponse loginResponse = new LoginResponse();
        loginResponse.setCode(ResultCode.SUCCESS.getCode());
        loginResponse.setMessage("login success");
        loginResponse.setToken(token);
        loginResponse.setRefreshToken(refreshToken);
        loginResponse.setUserName(user.getLoginName());
        loginResponse.setUserId(userId);
        loginResponse.setRoleName(user.getRoleName());
        // 清除失败次数
        //redisUtil.remove(CommonConstant.USER_NO_KEY_LOGIN_TIMES + userName);
        redisUtil.remove(CommonConstant.USER_PASSWORD_ERROR_TIMES + userName);
        return loginResponse;
    }

    @Override
    public CommonResponse logout(HttpServletRequest request) {
        // 删除用户登陆缓存信息/RT/
        String header = request.getHeader("Authorization");
        String token = header.substring(7);
        String userInfo = AuthorizationService.verifyToken(token);
        String userId = userInfo.split("&")[0];
        boolean remove = redisUtil.remove(CommonConstant.USER_INFO_KEY_PREFIX + userId);
        if (remove) {
            return new CommonResponse(ResultCode.SUCCESS);
        }
        return CommonResponse.fail(ResultCode.FAIL);
    }

    @Override
    public CommonResponse generateLoginQrCode(String userName, String password) {
        UserDTO userInfo = userMapper.getSecretKey(userName, password);
        if (ObjectUtils.isEmpty(userInfo)) {
            return CommonResponse.fail(ResultCode.USER_VERIFY_FAIL, "check user info failed");
        }
        String localSecretKey = userInfo.getLoginSecret();
        if (localSecretKey == null) {
            localSecretKey = GoogleUtil.getSecretKey();
            // 往数据库存入该用户的密钥
            int result = userMapper.bingSecretKey(localSecretKey, userName);
            if (result == 0) {
                return new CommonResponse(ResultCode.BIND_SECRET_KEY_FAIL);
            }
        }
        String secretKey = localSecretKey == null ? GoogleUtil.getSecretKey() : localSecretKey;

        String qrCode = GoogleUtil.getQrCode(secretKey, userName);
        return CommonResponse.success(qrCode);
    }

    @Override
    public CommonResponse refreshAuthToken(String refreshToken, HttpServletRequest request) {
        String ip = request.getParameter(CommonConstant.ATTR_IP);
        String userAgent = request.getParameter(CommonConstant.ATTR_USERAGENT);
        // 从refreshToken中获取用户账号
        String userInfos = AuthorizationService.verifyToken(refreshToken);
        if (userInfos == null) {
            // RT过期，前端需要重新跳转登陆页面让用户重新登陆
            return new CommonResponse(ResultCode.REFRESH_TOKEN_EXPIRE);
        }
        String account = userInfos.split("&")[0];
        String userName = userInfos.split("&")[1];
        // 创建新的token
        String accessToken = authorizationService.createAccessIdToken(account, userName, ip, userAgent);
        // 判断refreshToken是否要过期 即将过期则刷新RT
        long minTimeOfRefreshToken = 2 * AuthorizationService.accessTokenExpirationTime; //refreshToken剩余时常保证在token有效期的2倍以上，否则刷新RT
        Long refreshTokenStartTime = redisUtil.getValue(CommonConstant.REFRESH_TOKEN_START_TIME_PREFIX + account) == null ? null : Long.parseLong(redisUtil.getValue(CommonConstant.REFRESH_TOKEN_START_TIME_PREFIX + account));
        if (refreshTokenStartTime == null || (refreshTokenStartTime + AuthorizationService.refreshTokenExpirationTime * 1000) - System.currentTimeMillis() <= minTimeOfRefreshToken * 1000) {
            // 刷新refreshToken
            refreshToken = authorizationService.createRefreshToken(account, userName, ip, userAgent);
            redisUtil.setWithSecondExpire(CommonConstant.REFRESH_TOKEN_START_TIME_PREFIX + account, String.valueOf(System.currentTimeMillis()), (int) AuthorizationService.refreshTokenExpirationTime);
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
