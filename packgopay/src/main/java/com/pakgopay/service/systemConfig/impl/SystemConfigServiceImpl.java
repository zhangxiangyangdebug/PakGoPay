package com.pakgopay.service.systemConfig.impl;

import com.pakgopay.common.enums.ResultCode;
import com.pakgopay.common.exception.PakGoPayException;
import com.pakgopay.common.reqeust.roleManagement.AddRoleRequest;
import com.pakgopay.common.reqeust.roleManagement.DeleteRoleRequest;
import com.pakgopay.common.reqeust.roleManagement.ModifyRoleRequest;
import com.pakgopay.common.response.CommonResponse;
import com.pakgopay.common.response.RoleInfoResponse;
import com.pakgopay.common.response.roleManagement.RoleMenuInfoResponse;
import com.pakgopay.mapper.RoleMapper;
import com.pakgopay.mapper.RoleMenuMapper;
import com.pakgopay.mapper.UserMapper;
import com.pakgopay.mapper.dto.Role;
import com.pakgopay.mapper.dto.RoleMenuDTO;
import com.pakgopay.mapper.dto.UserDTO;
import com.pakgopay.service.common.AuthorizationService;
import com.pakgopay.service.systemConfig.SystemConfigService;
import com.pakgopay.thirdUtil.GoogleUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
public class SystemConfigServiceImpl implements SystemConfigService {

    private final RoleMapper roleMapper;
    private final UserMapper userMapper;
    private final RoleMenuMapper roleMenuMapper;

    public SystemConfigServiceImpl(RoleMapper roleMapper, UserMapper userMapper, RoleMenuMapper roleMenuMapper) {
        this.roleMapper = roleMapper;
        this.userMapper = userMapper;
        this.roleMenuMapper = roleMenuMapper;
    }

    @Override
    public CommonResponse roleList(String roleName) {
        List<Role> roleList = null;
        if (StringUtils.isEmpty(roleName)) {
            roleList = roleMapper.getRoleList();
        } else {
            roleList = roleMapper.getRoleListByRoleName(roleName);
        }

        if (roleList.isEmpty()) {
            return null;
        }
        DateTimeFormatter sdf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        List<RoleInfoResponse> roleInfoResponses = new ArrayList<>();
        roleList.forEach(role -> {
            RoleInfoResponse roleInfoResponse = new RoleInfoResponse();
            roleInfoResponse.setRoleId(role.getRoleId());
            roleInfoResponse.setRoleName(role.getRoleName());
            roleInfoResponse.setCreateTime(role.getCreateTime().toString());
            roleInfoResponse.setUpdateTime(role.getUpdateTime()==null? null:role.getUpdateTime().toString());
            roleInfoResponses.add(roleInfoResponse);
        });
        return CommonResponse.success(roleInfoResponses);
    }

    @Override
    public CommonResponse loginUserList() {
        List<UserDTO> loginUsers = userMapper.selectAllUser();
        if (loginUsers.isEmpty()) {
            return CommonResponse.success(null);
        }
        return CommonResponse.success(loginUsers);
    }

    @Override
    public CommonResponse manageLoginUserStatus(String userId, Integer status, Integer googleCode, String operatorId) {
        // 校验Google
        String operatorSecretKey = userMapper.getSecretKeyByUserId(operatorId);
        boolean googleResult = GoogleUtil.verifyQrCode(operatorSecretKey, googleCode);
        if (!googleResult) {
            return CommonResponse.fail(ResultCode.CODE_IS_EXPIRE);
        }
        int stopResult = userMapper.stopLoginUser(userId, status);
        if (stopResult == 0) {
            return CommonResponse.fail(ResultCode.FAIL, "stop login user failed");
        }
        return CommonResponse.success(ResultCode.SUCCESS);
    }

    @Override
    public CommonResponse deleteLoginUser(String userId, Integer googleCode, String operatorId) {

        String operatorKey = userMapper.getSecretKeyByUserId(operatorId);

        if(!GoogleUtil.verifyQrCode(operatorKey, googleCode)){
            return CommonResponse.fail(ResultCode.CODE_IS_EXPIRE);
        }
        // the super user is not allowed to delete
        UserDTO userInfo = userMapper.getOneUserByUserId(userId);
        if (userInfo == null) {
            return CommonResponse.fail(ResultCode.FAIL, "delete login user failed, cannot find user");
        }
        if (userInfo.getLoginName().equals("admin")) {
            return  CommonResponse.fail(ResultCode.FAIL, "this user is not allowed to delete.");
        }

        int deleteResult = userMapper.deleteUserByUserId(userId);
        if (deleteResult == 0) {
            return CommonResponse.fail(ResultCode.FAIL, "delete login user failed");
        }
        return CommonResponse.success("delete login user success");
    }

    @Override
    public CommonResponse loginUserByLoginName(String loginName) {
        UserDTO userDTO = userMapper.loginUserByLoginName(loginName);
        return CommonResponse.success(userDTO);
    }

    @Override
    @Transactional
    public CommonResponse addRoleInfo(AddRoleRequest addRoleRequest, HttpServletRequest request) {
        Long googleCode = addRoleRequest.getGoogleCode();
        try {
            String userInfo = checkGoogleCode(googleCode, request);
            if (userInfo == null) {
                return CommonResponse.fail(ResultCode.CODE_IS_EXPIRE);
            }
            String operatorName = userInfo.split("&")[1];
            Role role = new Role();
            role.setRoleName(addRoleRequest.getRoleName());
            role.setCreateTime(Instant.now().getEpochSecond());
            role.setRemark(addRoleRequest.getRemark());
            // insert role info add get role id
            Integer result = roleMapper.addNewRole(role);
            if (result == 0) {
                return CommonResponse.fail(ResultCode.FAIL, "add role failed");
            } else {
                List<Role> newRole = roleMapper.getRoleListByRoleName(addRoleRequest.getRoleName());
                Integer roleId = newRole.get(0).getRoleId();
                List<RoleMenuDTO> roleMenus = new ArrayList<>();
                addRoleRequest.getMenuList().forEach(addMenuId -> {
                    RoleMenuDTO roleMenuDTO = new RoleMenuDTO();
                    roleMenuDTO.setRoleId(roleId);
                    roleMenuDTO.setMenuId(addMenuId);
                    roleMenuDTO.setCreator(operatorName);
                    roleMenuDTO.setCreateTime(Instant.now().getEpochSecond());
                    roleMenus.add(roleMenuDTO);
                });
                roleMenuMapper.addRoleMenu(roleMenus);
            }
        } catch (PakGoPayException pe) {
            return CommonResponse.fail(ResultCode.FAIL, pe.getMessage());
        }
        catch (Exception se) {
              return CommonResponse.fail(ResultCode.FAIL, "add new role failed, the error is " + se.getMessage());
        }
        return CommonResponse.success(ResultCode.SUCCESS);
    }

    @Override
    public CommonResponse getRoleMenuInfoByRoleId(Integer menuId) {

        List<RoleMenuDTO> roleMenuByRoleId = roleMenuMapper.getRoleMenuInfoByRoleId(menuId);
        if (roleMenuByRoleId.isEmpty()) {
            return CommonResponse.fail(ResultCode.FAIL, "get role info failed");
        }
        RoleMenuInfoResponse response = new RoleMenuInfoResponse();
        response.setRoleId(roleMenuByRoleId.get(0).getRoleId());
        response.setRoleName(roleMenuByRoleId.get(0).getRoleName());
        response.setRemark(roleMenuByRoleId.get(0).getRemark());
        List<String> menuList = new ArrayList<>();
        roleMenuByRoleId.forEach(roleMenuDTO -> {
           if (roleMenuDTO.getMenuId().length() > 3) {
               menuList.add(roleMenuDTO.getMenuId());
           }

        });
        response.setMenuList(menuList);
        return CommonResponse.success(response);
    }

    @Override
    @Transactional
    public CommonResponse modifyRoleInfo(ModifyRoleRequest modifyRoleRequest, HttpServletRequest request) {
        String operatorInfo = null;
        try {
            operatorInfo = checkGoogleCode(modifyRoleRequest.getGoogleCode(), request);
            if (operatorInfo == null) {
                return CommonResponse.fail(ResultCode.CODE_IS_EXPIRE);
            }
            String operatorName = operatorInfo.split("&")[1];
            List<RoleMenuDTO> roleMenuDTOS = new ArrayList<>();
            modifyRoleRequest.getMenuList().forEach(addMenuId -> {
                RoleMenuDTO roleMenuDTO = new RoleMenuDTO();
                roleMenuDTO.setRoleId(modifyRoleRequest.getRoleId());
                roleMenuDTO.setRoleName(modifyRoleRequest.getRoleName());
                roleMenuDTO.setCreator(operatorName);
                roleMenuDTO.setCreateTime(Instant.now().getEpochSecond());
                roleMenuDTO.setRemark(modifyRoleRequest.getRemark());
                roleMenuDTO.setMenuId(addMenuId);
                roleMenuDTOS.add(roleMenuDTO);
            });
            Integer deleteResult = roleMenuMapper.deleteRoleMenuByRoleId(modifyRoleRequest.getRoleId());
            /*if (deleteResult == 0) {
                return CommonResponse.fail(ResultCode.FAIL, "modify role info failed");
            }*/
            Integer addResult = roleMenuMapper.addRoleMenu(roleMenuDTOS);
            /*if (addResult == 0) {
                return CommonResponse.fail(ResultCode.FAIL, "modify role info failed when insert role info");
            }*/
            return CommonResponse.success(ResultCode.SUCCESS);
        } catch (PakGoPayException e) {
            return CommonResponse.fail(e);
        } catch (Exception se) {
            return CommonResponse.fail(ResultCode.FAIL, "modify role info failed, the error is " + se.getMessage());
        }
    }

    public String checkGoogleCode(Long googleCode, HttpServletRequest request) throws PakGoPayException {
        String header = request.getHeader("Authorization");
        String token = header.substring(7);
        String userInfo = AuthorizationService.verifyToken(token);
        String operator = userInfo.split("&")[0];
        String secretKey = null;
        try {
            secretKey = userMapper.getSecretKeyByUserId(operator);
        } catch (Exception e) {
            throw new PakGoPayException(ResultCode.FAIL,"get secret key for operator failed");
        }
        if(GoogleUtil.verifyQrCode(secretKey, googleCode)){
            return userInfo;
        }
        return null;
    }

    @Override
    @Transactional
    public CommonResponse deleteRole(DeleteRoleRequest deleteRoleRequest, HttpServletRequest request) {
        try {
            String userInfo = checkGoogleCode(deleteRoleRequest.getGoogleCode(), request);
            if (userInfo == null) {
                return CommonResponse.fail(ResultCode.CODE_IS_EXPIRE);
            }
            Integer deleteRole = roleMapper.deleteRole(deleteRoleRequest.getRoleId());
            if (deleteRole == 0) {
                throw new PakGoPayException(ResultCode.FAIL, "delete role failed");
            }
            Integer deleteRoleMenuResult = roleMenuMapper.deleteRoleMenuByRoleId(deleteRoleRequest.getRoleId());
            if (deleteRoleMenuResult == 0) {
                throw new PakGoPayException(ResultCode.FAIL, "delete role menu failed");
            }
            return CommonResponse.success(ResultCode.SUCCESS);
        } catch (PakGoPayException e) {
            return CommonResponse.fail(e);
        } catch (Exception e) {
            return CommonResponse.fail(ResultCode.FAIL, "delete role failed, the error is " + e.getMessage());
        }
    }
}
