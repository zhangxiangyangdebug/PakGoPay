package com.pakgopay.service.common;

import com.pakgopay.common.constant.CommonConstant;
import com.pakgopay.common.enums.ResultCode;
import com.pakgopay.common.exception.PakGoPayException;
import com.pakgopay.mapper.RoleMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class CommonService {
    @Autowired
    private RoleMapper roleMapper;

    public Integer getRoleIdByUserId(String userId) throws PakGoPayException {
        Integer roleId = 0;
        try {
            roleId = roleMapper.queryRoleInfoByUserId(userId);
        } catch (Exception e) {
            log.error("balance findByTransactionNo failed, message {}", e.getMessage());
            throw new PakGoPayException(ResultCode.DATA_BASE_ERROR);
        }
        if (roleId == null || CommonConstant.ROLE_UNKNOWN == roleId) {
            log.error("user has not role");
            throw new PakGoPayException(ResultCode.INVALID_PARAMS, "user has not role");
        }
        return roleId;
    }

}
