package com.pakgopay.controller;


import com.pakgopay.common.response.CommonResponse;
import com.pakgopay.service.impl.MenuItemServiceImpl;
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
    public CommonResponse menu(){
        CommonResponse menu = null;
        try {
            menu = menuItemService.menu();
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
        return menu;
    }
}
