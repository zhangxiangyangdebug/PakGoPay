package com.pakgopay.service.login.impl;

import com.alibaba.fastjson.JSON;
import com.pakgopay.common.entity.Options;
import com.pakgopay.common.enums.ResultCode;
import com.pakgopay.common.response.CommonResponse;
import com.pakgopay.mapper.RoleMapper;
import com.pakgopay.mapper.UserMapper;
import com.pakgopay.mapper.dto.Role;
import com.pakgopay.mapper.dto.UserDTO;
import com.pakgopay.thirdUtil.GoogleUtil;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class SystemConfigService {

    private final RoleMapper roleMapper;
    private final UserMapper userMapper;

    public SystemConfigService(RoleMapper roleMapper, UserMapper userMapper) {
        this.roleMapper = roleMapper;
        this.userMapper = userMapper;
    }

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

    public CommonResponse loginUserList() {
        List<UserDTO> loginUsers = userMapper.selectAllUser();
        if (loginUsers.isEmpty()) {
            return CommonResponse.success(null);
        }
        return CommonResponse.success(loginUsers);
    }

    public CommonResponse stopLoginUser(String userId, Integer googleCode, String operatorId) {
        // 校验Google
        String operatorSecretKey = userMapper.getSecretKeyByUserId(operatorId);
        boolean googleResult = GoogleUtil.verifyQrCode(operatorSecretKey, googleCode);
        if (!googleResult) {
            return CommonResponse.fail(ResultCode.CODE_IS_EXPIRE);
        }
        int stopResult = userMapper.stopLoginUser(userId);
        if (stopResult == 0) {
            return CommonResponse.fail(ResultCode.FAIL, "stop login user failed");
        }
        return CommonResponse.success(ResultCode.SUCCESS);
    }
}
