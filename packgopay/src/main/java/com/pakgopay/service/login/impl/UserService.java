package com.pakgopay.service.login.impl;

import com.pakgopay.entity.User;
import com.pakgopay.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class UserService {
    @Autowired
    private UserMapper userMapper;

    public User selectAllUser(){
        return userMapper.selectAllUser();
    }
}
