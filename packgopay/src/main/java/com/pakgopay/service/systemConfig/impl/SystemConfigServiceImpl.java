package com.pakgopay.service.systemConfig.impl;

import com.pakgopay.common.enums.ResultCode;
import com.pakgopay.common.response.CommonResponse;
import com.pakgopay.mapper.RoleMapper;
import com.pakgopay.mapper.UserMapper;
import com.pakgopay.mapper.dto.Role;
import com.pakgopay.mapper.dto.UserDTO;
import com.pakgopay.service.systemConfig.SystemConfigService;
import com.pakgopay.thirdUtil.GoogleUtil;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SystemConfigServiceImpl implements SystemConfigService {

    private final RoleMapper roleMapper;
    private final UserMapper userMapper;

    public SystemConfigServiceImpl(RoleMapper roleMapper, UserMapper userMapper) {
        this.roleMapper = roleMapper;
        this.userMapper = userMapper;
    }

    @Override
    public CommonResponse roleList() {

        List<Role> roleList = roleMapper.getRoleList();
        if (roleList.isEmpty()) {
            return null;
        }

        /*List<Options> options = new ArrayList<>();
        roleList.forEach(role -> {
            Options option = new Options();
            option.setValue(role.getRoleId());
            option.setLabel(role.getRoleName());
            options.add(option);
        });*/
        return CommonResponse.success(roleList);
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
        // the super user is not allowed to delete
        UserDTO userInfo = userMapper.getOneUserByUserId(userId);
        if (userInfo == null) {
            return CommonResponse.fail(ResultCode.FAIL, "delete login user failed, cannot find user");
        }
        if (userInfo.getLoginName().equals("admin")) {
            return  CommonResponse.fail(ResultCode.FAIL, "this user is not allowed to delete.");
        }
        String operatorKey = userMapper.getSecretKeyByUserId(operatorId);

        if(!GoogleUtil.verifyQrCode(operatorKey, googleCode)){
            return CommonResponse.fail(ResultCode.CODE_IS_EXPIRE);
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
}
