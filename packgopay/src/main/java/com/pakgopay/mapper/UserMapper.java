package com.pakgopay.mapper;

import com.pakgopay.mapper.dto.UserDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface UserMapper {

     List<UserDTO> selectAllUser();

     UserDTO loginUserByLoginName(@Param(value = "loginName") String loginName);

     UserDTO getOneUser(@Param(value = "userName") String userName, @Param(value = "password") String password);

     UserDTO getOneUserByUserId(@Param(value = "userId") String userId);

     UserDTO getSecretKey(@Param(value = "userName") String userId, @Param(value = "password") String password);

     int bingSecretKey(@Param(value = "secretKey")  String secretKey,@Param(value = "userName") String userName);

     int setLastLoginTime(@Param(value = "lastLoginTime") Long lastLoginTime, @Param(value = "userId") String userId);

     UserDTO findRoleId(@Param(value = "userName") String userName);

     int createUser(@Param(value="user") UserDTO user);

     String getSecretKeyByUserId(@Param(value = "userId") String userId);

     int stopLoginUser(@Param(value = "userId") String userId, @Param(value = "status") Integer status);

    int deleteUserByUserId(String userId);
}
