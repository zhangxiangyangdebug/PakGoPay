package com.pakgopay.mapper;

import com.pakgopay.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserMapper {

     User selectAllUser();

     User getOneUser(@Param(value = "userId") String userId, @Param(value = "password") String password);

     User getOneUserByUsername(@Param(value = "userId") Integer userId);

     User getSecretKey(@Param(value = "userId") String userId, @Param(value = "password") String password);

     int bingSecretKey(@Param(value = "secretKey")  String secretKey,@Param(value = "userId") String userId);

     int setLastLoginTime(@Param(value = "lastLoginTime") String lastLoginTime, @Param(value = "userId") String userId);
}
