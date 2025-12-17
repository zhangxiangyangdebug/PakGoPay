package com.pakgopay.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface RoleMapper {

    Integer queryRoleInfoByUserId(@Param(value = "userId") Integer userId);
}
