package com.pakgopay.controller;

import com.pakgopay.common.reqeust.CreateUserRequest;
import com.pakgopay.common.response.CommonResponse;
import com.pakgopay.service.impl.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/pakGoPay/server/SystemConfig")
public class SystemConfigController {

    @Autowired
    private UserService userService;

    @RequestMapping("/createUser")
    public CommonResponse createLoginUser(@RequestBody CreateUserRequest createUserRequest){
        return userService.createLoginUser(createUserRequest);
    }
}
