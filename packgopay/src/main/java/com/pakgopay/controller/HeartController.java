package com.pakgopay.controller;

import com.pakgopay.service.common.AuthorizationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/pakGoPay/server")
public class HeartController {

    @GetMapping("/heart")
    public String heart(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        String token = authorization != null && authorization.startsWith("Bearer ") ? authorization.substring(7) : null;
        if (token != null && AuthorizationService.verifyToken(token) != null) {
            // 有效token
            return "success";
        } else {
            return "refresh";
        }
    }
}
