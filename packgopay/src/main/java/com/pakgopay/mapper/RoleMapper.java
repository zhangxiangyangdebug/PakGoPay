package com.pakgopay.mapper;

import com.pakgopay.mapper.dto.Role;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface RoleMapper {

    Integer queryRoleInfoByUserId(@Param(value = "userId") String userId);

    List<Role> getRoleList();

    List<Role> getRoleListByRoleName(@Param(value = "roleName") String roleName);

    Integer addNewRole(@Param(value = "role") Role role);

    Integer deleteRole(@Param(value = "roleId") Integer roleId);
}
