package com.pakgopay.service;

import com.pakgopay.data.reqeust.roleManagement.AddRoleRequest;
import com.pakgopay.data.reqeust.roleManagement.DeleteRoleRequest;
import com.pakgopay.data.reqeust.roleManagement.ModifyRoleRequest;
import com.pakgopay.data.response.CommonResponse;
import jakarta.servlet.http.HttpServletRequest;

public interface SystemConfigService {

    public CommonResponse listRoles(String roleName);

    public CommonResponse listLoginUsers();

    public CommonResponse updateLoginUserStatus(String userId, Integer status, Integer googleCode, String operatorId);

    public CommonResponse deleteLoginUser(String userId, Integer googleCode, String operatorId);

    public CommonResponse fetchLoginUserByLoginName(String loginName);

    public CommonResponse createRole(AddRoleRequest addRoleRequest, HttpServletRequest request);

    public CommonResponse fetchRoleMenuByRoleId(Integer menuId);

    public CommonResponse updateRole(ModifyRoleRequest modifyRoleRequest, HttpServletRequest request);

    public CommonResponse deleteRole(DeleteRoleRequest deleteRoleRequest, HttpServletRequest request);
}
