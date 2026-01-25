package com.pakgopay.controller;


import com.pakgopay.data.response.CommonResponse;
import com.pakgopay.service.common.NotificationService;
import com.pakgopay.thirdUtil.RedisUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.annotations.Param;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/pakGoPay/server")
public class NotationController {

    @Autowired
    private NotificationService notificationService;

    @GetMapping("/notation/queryNotation")
    public CommonResponse queryNotation(HttpServletRequest request) {

        return notificationService.getUserNofityMessage(request);
    }

    @GetMapping("/notation/markNotation")
    public CommonResponse markRead(HttpServletRequest request) {
        System.out.println("sssss");
        String messageId = request.getParameter("messageId");
        return notificationService.markRead(request, messageId);
    }
}
