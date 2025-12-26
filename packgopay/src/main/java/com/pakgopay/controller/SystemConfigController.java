package com.pakgopay.controller;

import com.pakgopay.common.reqeust.CreateUserRequest;
import com.pakgopay.common.response.CommonResponse;
import com.pakgopay.mapper.RoleMapper;
import com.pakgopay.service.login.impl.SystemConfigService;
import com.pakgopay.service.login.impl.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/pakGoPay/server/SystemConfig")
public class SystemConfigController {

    @Autowired
    private UserService userService;

    @Autowired
    private SystemConfigService systemConfigService;

    @PostMapping("/createUser")
    public CommonResponse createLoginUser(@RequestBody CreateUserRequest createUserRequest){
        return userService.createLoginUser(createUserRequest);
    }


    @GetMapping("/roleList")
    public CommonResponse roleList(){
        return systemConfigService.roleList();
    }

    @GetMapping("/loginUserList")
    public CommonResponse loginUserList() {
        return systemConfigService.loginUserList();
    }

    @GetMapping("/stopLoginUser")
    public CommonResponse stopLoginUser(String userId, Integer googleCode, String operatorId){
        return systemConfigService.stopLoginUser(userId, googleCode, operatorId);
    }
}
