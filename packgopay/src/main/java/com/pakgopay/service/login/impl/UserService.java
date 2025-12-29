package com.pakgopay.service.login.impl;

import com.pakgopay.common.enums.ResultCode;
import com.pakgopay.common.reqeust.CreateUserRequest;
import com.pakgopay.common.response.CommonResponse;
import com.pakgopay.mapper.UserMapper;
import com.pakgopay.mapper.dto.UserDTO;
import com.pakgopay.thirdUtil.GoogleUtil;
import com.pakgopay.util.SnowflakeIdGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserService {
    @Autowired
    private UserMapper userMapper;

    public List<UserDTO> selectAllUser(){
        return userMapper.selectAllUser();
    }

    public CommonResponse createLoginUser(CreateUserRequest user){
        // check data
        if(!user.getPassword().equals( user.getConfirmPassword())){
            return CommonResponse.fail(ResultCode.FAIL, "check password is same with confirm password");
        }

        if(user.getOperatorId() == null){
            return CommonResponse.fail(ResultCode.FAIL, "operator is invalid");
        }

        UserDTO operator = userMapper.getOneUserByUserId(user.getOperatorId());


        //verify google code
        boolean googleResult = GoogleUtil.verifyQrCode(operator.getLoginSecret(), user.getGoogleCode());

        if (!googleResult){
            return CommonResponse.fail(ResultCode.FAIL, "check operator google code failed!");
        }
        // build userId
        SnowflakeIdGenerator snow = new SnowflakeIdGenerator();
        long userId = snow.nextId();
        UserDTO newUser = new UserDTO();
        newUser.setUserId(String.valueOf(userId));
        newUser.setPassword(user.getPassword());
        newUser.setStatus(user.getStatus());
        newUser.setLoginName(user.getLoginName());
        newUser.setRoleId(user.getRoleId());
        int result = 0;
        /*try {*/
            result = userMapper.createUser(newUser);
        /*} catch (Exception e){
            if (e.getMessage().contains("Duplicate entry")) {
                return CommonResponse.fail(ResultCode.FAIL, "login name is exist");
            }
        }*/

        if (result == 0){
            return CommonResponse.fail(ResultCode.FAIL, "create user failed");
        } else {
            return CommonResponse.success("Create user successfully");
        }
        //return userMapper.createLoginUser(user);
    }
}
