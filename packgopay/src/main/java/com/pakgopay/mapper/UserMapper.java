package com.pakgopay.mapper;

import com.pakgopay.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface UserMapper {

     User selectAllUser();

     User getOneUser(@Param(value = "username") String username, @Param(value = "password") String password);

     User getSecretKey(@Param(value = "username") String username);

     int bingSecretKey(@Param(value = "secretKey")  String secretKey,@Param(value = "username") String username);
}
