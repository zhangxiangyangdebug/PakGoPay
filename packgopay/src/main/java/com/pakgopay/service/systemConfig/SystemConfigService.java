package com.pakgopay.service.systemConfig;

import com.pakgopay.common.reqeust.roleManagement.AddRoleRequest;
import com.pakgopay.common.reqeust.roleManagement.DeleteRoleRequest;
import com.pakgopay.common.reqeust.roleManagement.ModifyRoleRequest;
import com.pakgopay.common.response.CommonResponse;
import jakarta.servlet.http.HttpServletRequest;

public interface SystemConfigService {

    public CommonResponse roleList(String roleName);

    public CommonResponse loginUserList();

    public CommonResponse manageLoginUserStatus(String userId, Integer status, Integer googleCode, String operatorId);

    public CommonResponse deleteLoginUser(String userId, Integer googleCode, String operatorId);

    public CommonResponse loginUserByLoginName(String loginName);

    public CommonResponse addRoleInfo(AddRoleRequest addRoleRequest, HttpServletRequest request);

    public CommonResponse getRoleMenuInfoByRoleId(Integer menuId);

    public CommonResponse modifyRoleInfo(ModifyRoleRequest modifyRoleRequest, HttpServletRequest request);

    public CommonResponse deleteRole(DeleteRoleRequest deleteRoleRequest, HttpServletRequest request);
}
