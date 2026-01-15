package com.pakgopay.controller;


import com.pakgopay.data.response.CommonResponse;
import com.pakgopay.service.common.AuthorizationService;
import com.pakgopay.service.impl.MenuItemServiceImpl;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/pakGoPay/server")
public class MenuController {

    @Autowired
    private MenuItemServiceImpl menuItemService;

    @GetMapping("/menu")
    public CommonResponse menu(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        String token = header.substring(7);
        String userInfo = AuthorizationService.verifyToken(token);
        String userId = userInfo.split("&")[0];
        CommonResponse menu = null;
        try {
            menu = menuItemService.menu(userId);
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
        return menu;
    }
}
