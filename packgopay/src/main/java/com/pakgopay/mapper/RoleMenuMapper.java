package com.pakgopay.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface RoleMenuMapper {

    List<String> getAllMenuIdsByRoleId(@Param(value = "roleId") Integer roleId);
}
