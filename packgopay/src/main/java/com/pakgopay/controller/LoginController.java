package com.pakgopay.controller;

import com.pakgopay.common.reqeust.LoginRequest;
import com.pakgopay.common.response.CommonResponse;
import com.pakgopay.entity.TestMessage;
import com.pakgopay.entity.User;
import com.pakgopay.service.LoginService;
import com.pakgopay.service.TestMq;
import com.pakgopay.service.impl.UserService;
import com.pakgopay.thirdUtil.GoogleUtil;
import com.pakgopay.thirdUtil.RedisUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/pakGoPay/server/Login")
public class LoginController {

    private static Logger logger = LogManager.getLogger("RollingFile");

    @Autowired
    private TestMq testMq;

    @Autowired
    private RedisUtil redisUtil;

    @Autowired
    private UserService userService;

    @Autowired
    private LoginService loginService;


    @RequestMapping(value = "/hello")
    public String test(){
        System.out.println("this is hello test");
        TestMessage testMessage = new TestMessage();
        testMessage.setContent("hello world");
        testMq.send("test", testMessage);
        testMessage.setContent("这是一个延迟消息");
        testMq.sendDelay("delay-test", testMessage);
        return "{'zf':'test'}";
    }

    @PostMapping(value = "/login")
    public CommonResponse login(HttpServletRequest request, @RequestBody LoginRequest loginRequest){
        CommonResponse commonResponse = loginService.login(loginRequest);
        return commonResponse;
    }

    @RequestMapping(value = "db")
    public String test3(){
        User user = userService.selectAllUser();
        System.out.println(user);
        return "test3";
    }

    @RequestMapping(value = "/getCode")
    public CommonResponse verify(@RequestParam String username){
        return loginService.getQrCode(username);
    }
}
