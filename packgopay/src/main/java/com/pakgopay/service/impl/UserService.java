package com.pakgopay.service.login.impl;

import com.pakgopay.common.enums.ResultCode;
import com.pakgopay.common.exception.PakGoPayException;
import com.pakgopay.data.reqeust.CreateUserRequest;
import com.pakgopay.data.response.CommonResponse;
import com.pakgopay.mapper.UserMapper;
import com.pakgopay.mapper.dto.UserDTO;
import com.pakgopay.util.SnowflakeIdGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class UserService {
    @Autowired
    private UserMapper userMapper;

    public List<UserDTO> selectAllUser() {
        return userMapper.selectAllUser();
    }

    public CommonResponse createLoginUser(CreateUserRequest user) {


        try {
            createUser(user);
            return CommonResponse.success("Create user successfully");
        } catch (PakGoPayException e) {
            log.error("createLoginUser failed, code: {} message: {}", e.getErrorCode(), e.getMessage());
            return CommonResponse.fail(e.getCode(), "createLoginUser failed: " + e.getMessage());
        }
    }

    public long createUser(CreateUserRequest user) throws PakGoPayException {
        // check data
        if (!user.getPassword().equals(user.getConfirmPassword())) {
            throw new PakGoPayException(ResultCode.FAIL, "check password is same with confirm password");
        }

        if (user.getOperatorId() == null) {
            throw new PakGoPayException(ResultCode.FAIL, "operator is invalid");
        }

        UserDTO operator = userMapper.getOneUserByUserId(user.getOperatorId());


        //verify google code TODO 转移至filter
//        boolean googleResult = GoogleUtil.verifyQrCode(operator.getLoginSecret(), user.getGoogleCode());
//
//        if (!googleResult) {
//            throw new PakGoPayException(ResultCode.FAIL, "check operator google code failed!");
//        }
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

        if (result == 0) {
            throw new PakGoPayException(ResultCode.FAIL, "create user failed");
        }

        return userId;
    }
}
