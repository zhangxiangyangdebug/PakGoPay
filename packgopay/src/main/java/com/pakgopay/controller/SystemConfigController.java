package com.pakgopay.controller;

import com.pakgopay.data.reqeust.CreateUserRequest;
import com.pakgopay.data.reqeust.roleManagement.AddRoleRequest;
import com.pakgopay.data.reqeust.roleManagement.DeleteRoleRequest;
import com.pakgopay.data.reqeust.roleManagement.ModifyRoleRequest;
import com.pakgopay.data.response.CommonResponse;
import com.pakgopay.service.SystemConfigService;
import com.pakgopay.service.impl.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/pakGoPay/server/SystemConfig")
public class SystemConfigController {

    @Autowired
    private UserService userService;

    @Autowired
    private SystemConfigService systemConfigService;

    @PostMapping("/createUser")
    public CommonResponse createLoginUser(@RequestBody CreateUserRequest createUserRequest){
        return userService.createLoginUser(createUserRequest);
    }


    @GetMapping("/roleList")
    public CommonResponse roleList(@RequestParam(required = false) String roleName){
        return systemConfigService.roleList(roleName);
    }

    @GetMapping("/loginUserList")
    public CommonResponse loginUserList() {
        return systemConfigService.loginUserList();
    }

    @GetMapping("/manageLoginUserStatus")
    public CommonResponse manageLoginUserStatus(String userId, Integer status, Integer googleCode, String operatorId){
        return systemConfigService.manageLoginUserStatus(userId, status, googleCode, operatorId);
    }

    @GetMapping("/deleteLoginUser")
    public CommonResponse deleteLoginUser(String userId, Integer googleCode, String operatorId){
        return systemConfigService.deleteLoginUser(userId, googleCode, operatorId);
    }

    @GetMapping("/loginUserByLoginName")
    public CommonResponse loginUserByLoginName(String loginName){
        return systemConfigService.loginUserByLoginName(loginName);
    }

    @PostMapping("/addRole")
    public CommonResponse addRoleInfo(@RequestBody AddRoleRequest addRoleRequest, HttpServletRequest request){
        return systemConfigService.addRoleInfo(addRoleRequest, request);
    }

    @PostMapping("/modifyRoleInfo")
    public CommonResponse modifyRoleInfo(@RequestBody ModifyRoleRequest modifyRoleRequest, HttpServletRequest request){
        return systemConfigService.modifyRoleInfo(modifyRoleRequest, request);
    }

    @PostMapping("/deleteRole")
    public CommonResponse deleteRole(@RequestBody DeleteRoleRequest deleteRoleRequest, HttpServletRequest request){
        return systemConfigService.deleteRole(deleteRoleRequest, request);
    }

    @GetMapping("/getRoleInfoByRoleId")
    public CommonResponse getRoleInfoOfMenu(Integer roleId) {
        return systemConfigService.getRoleMenuInfoByRoleId(roleId);
    }
}
