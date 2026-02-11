package com.pakgopay.controller;

import com.pakgopay.data.entity.Message;
import com.pakgopay.data.reqeust.LoginRequest;
import com.pakgopay.data.response.CommonResponse;
import com.pakgopay.service.common.AuthorizationService;
import com.pakgopay.service.LoginService;
import com.pakgopay.service.common.SendDmqMessage;
import com.pakgopay.service.impl.UserService;
import com.pakgopay.thirdUtil.GoogleUtil;
import com.pakgopay.thirdUtil.RedisUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.text.SimpleDateFormat;
import java.util.*;

@RestController
@RequestMapping("/pakGoPay/server/Login")
public class LoginController {

    private static Logger logger = LogManager.getLogger("RollingFile");

    @Autowired
    private SendDmqMessage testMq;

    @Autowired
    private RedisUtil redisUtil;

    @Autowired
    private UserService userService;

    @Autowired
    private LoginService loginService;

    @Autowired
    private AuthorizationService authorizationService;

    @RequestMapping(value = "/hello")
    public CommonResponse<Object> test(HttpServletRequest request) {
        String userInfoFromToken = GoogleUtil.getUserInfoFromToken(request);
        String userId = userInfoFromToken.split("&")[0];
        System.out.println("this is hello test");
        Message message = new Message();
        message.setContent("this is message test");
        message.setId(new SimpleDateFormat("yyyyMMddHHmmss").format(new Date())+ UUID.randomUUID().toString());
        message.setTimestamp(Calendar.getInstance().getTimeInMillis());
        message.setUserId(userId);
        message.setRead(false);
        redisUtil.saveMessage(message);
        testMq.sendFanout("user-notify", message);
        /*testMessage.setContent("你滴，大大的良民");*/
       /* testMq.sendDelay("test-delay-L10S", testMessage);*/
        return CommonResponse.success("success");
    }

    @PostMapping(value = "/login")
    public CommonResponse login(HttpServletRequest request, @RequestBody LoginRequest loginRequest){
        CommonResponse commonResponse = loginService.login(loginRequest, request);
        return commonResponse;
    }

    @GetMapping(value = "/logout")
    public CommonResponse logout(HttpServletRequest request){
        CommonResponse commonResponse = loginService.logout(request);
        return commonResponse;
    }

    @RequestMapping(value = "db")
    public String test3(){
        /*List<UserDTO> user = userService.listUsers();
        System.out.println(user);*/
        return "test3";
    }

    /**
     * 此接口不需要token校验
     * 用refreshToken刷新accessToken
     * @param freshToken
     * @return
     */
    @GetMapping("/refreshToken")
    public CommonResponse accessTokenRefresh(@RequestParam String freshToken, HttpServletRequest request){
        return loginService.refreshAuthToken(freshToken, request);
    }

    /**
     * 获取谷歌令牌绑定二维码
     * @param userName
     * @return
     */
    @RequestMapping(value = "/getCode")
    public CommonResponse verify(@RequestParam(value = "userName")String userName, @RequestParam(value = "password") String password){
        return loginService.generateLoginQrCode(userName, password);
    }
}
