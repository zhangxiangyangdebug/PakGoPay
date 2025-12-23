package com.pakgopay.controller;

import com.pakgopay.common.enums.ResultCode;
import com.pakgopay.common.reqeust.LoginRequest;
import com.pakgopay.common.response.CommonResponse;
import com.pakgopay.entity.TestMessage;
import com.pakgopay.entity.User;
import com.pakgopay.service.LoginService;
import com.pakgopay.service.TestMq;
import com.pakgopay.service.impl.UserService;
import com.pakgopay.thirdUtil.RedisUtil;
import com.pakgopay.service.AuthorizationService;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

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

    @Autowired
    private AuthorizationService authorizationService;


    @RequestMapping(value = "/hello")
    public String test(){
        System.out.println("this is hello test");
        TestMessage testMessage = new TestMessage();
        testMessage.setContent("hello world");
        testMq.send("test", testMessage);
        testMessage.setContent("这是一个延迟消息");
        testMq.sendDelay("test-delay-L10S", testMessage);
        return "{'zf':'test'}";
    }

    @PostMapping(value = "/login")
    public CommonResponse login(HttpServletRequest request, @RequestBody LoginRequest loginRequest){
        CommonResponse commonResponse = loginService.login(loginRequest);
        return commonResponse;
    }

    @GetMapping(value = "/logout")
    public CommonResponse logout(HttpServletRequest request){
        CommonResponse commonResponse = loginService.logout(request);
        return commonResponse;
    }

    @RequestMapping(value = "db")
    public String test3(){
        User user = userService.selectAllUser();
        System.out.println(user);
        return "test3";
    }

    /**
     * 此接口不需要token校验
     * 用refreshToken刷新accessToken
     * @param freshToken
     * @return
     */
    @GetMapping("/refreshToken")
    public CommonResponse accessTokenRefresh(@RequestParam String freshToken){
        return loginService.refreshToken(freshToken);
    }

    /**
     * 获取谷歌令牌绑定二维码
     * @param userId
     * @return
     */
    @RequestMapping(value = "/getCode")
    public CommonResponse verify(@RequestParam String userId, @RequestParam String password){
        return loginService.getQrCode(userId, password);
    }
}
