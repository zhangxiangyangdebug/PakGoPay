package com.pakgopay.mapper;

import com.pakgopay.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;

@Mapper
public interface UserMapper {

     User selectAllUser();

     User getOneUser(@Param(value = "userId") String userId, @Param(value = "password") String password);

     User getOneUserByUsername(@Param(value = "userId") Integer userId);

     User getSecretKey(@Param(value = "userId") Integer userId, @Param(value = "password") String password);

     int bingSecretKey(@Param(value = "secretKey")  String secretKey,@Param(value = "userId") Integer userId);

     int setLastLoginTime(@Param(value = "lastLoginTime") String lastLoginTime, @Param(value = "userId") String userId);
}
