package com.pakgopay.service.systemConfig;

import com.pakgopay.common.response.CommonResponse;

public interface SystemConfigService {

    public CommonResponse roleList();

    public CommonResponse loginUserList();

    public CommonResponse manageLoginUserStatus(String userId, Integer status, Integer googleCode, String operatorId);

    public CommonResponse deleteLoginUser(String userId, Integer googleCode, String operatorId);

    public CommonResponse loginUserByLoginName(String loginName);


}
