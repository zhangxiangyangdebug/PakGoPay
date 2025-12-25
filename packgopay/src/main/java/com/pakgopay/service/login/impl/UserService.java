package com.pakgopay.service.login.impl;

import com.pakgopay.common.enums.ResultCode;
import com.pakgopay.common.reqeust.CreateUserRequest;
import com.pakgopay.common.response.CommonResponse;
import com.pakgopay.dto.UserDTO;
import com.pakgopay.entity.User;
import com.pakgopay.mapper.UserMapper;
import com.pakgopay.thirdUtil.GoogleUtil;
import com.pakgopay.util.SnowflakeIdGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class UserService {
    @Autowired
    private UserMapper userMapper;

    public User selectAllUser(){
        return userMapper.selectAllUser();
    }

    public CommonResponse createLoginUser(CreateUserRequest user){
        // check data
        if(user.getPassword() != user.getConfirmPassword()){
            return CommonResponse.fail(ResultCode.FAIL, "check password is same with confirm password");
        }

        if(user.getOperatorId() == null){
            return CommonResponse.fail(ResultCode.FAIL, "operator is invalid");
        }

        User operator = userMapper.getOneUserByUsername(Integer.parseInt(user.getOperatorId()));


        //verify google code
        boolean googleResult = GoogleUtil.verifyQrCode(operator.getSecretKey(), user.getGoogleCode());

        if (!googleResult){
            return CommonResponse.fail(ResultCode.FAIL, "check operator google code failed!");
        }
        // build userId
        SnowflakeIdGenerator snow = new SnowflakeIdGenerator();
        long userId = snow.nextId();
        UserDTO newUser = new UserDTO();
        newUser.setUserId(String.valueOf(userId));
        newUser.setPassword(user.getPassword());
        newUser.setStatus(user.getUserStatus());
        newUser.setUserName(user.getUsername());
        userMapper.createUser(newUser);
        return null;
        //return userMapper.createLoginUser(user);
    }
}
