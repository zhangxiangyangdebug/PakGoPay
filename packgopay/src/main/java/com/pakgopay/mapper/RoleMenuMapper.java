package com.pakgopay.mapper;

import com.pakgopay.mapper.dto.RoleMenuDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface RoleMenuMapper {

    List<String> getAllMenuIdsByRoleId(@Param(value = "roleId") Integer roleId);

    Integer addRoleMenu(@Param(value = "info")List<RoleMenuDTO> info);

    List<RoleMenuDTO> getRoleMenuInfoByRoleId(@Param(value = "roleId") Integer roleId);

    Integer deleteRoleMenuByRoleId(@Param(value = "roleId") Integer roleId);
}
