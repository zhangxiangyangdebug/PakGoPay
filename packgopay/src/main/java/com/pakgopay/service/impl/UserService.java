package com.pakgopay.service.impl;

import com.pakgopay.common.enums.ResultCode;
import com.pakgopay.common.exception.PakGoPayException;
import com.pakgopay.data.reqeust.CreateUserRequest;
import com.pakgopay.data.response.CommonResponse;
import com.pakgopay.mapper.BalanceMapper;
import com.pakgopay.mapper.UserMapper;
import com.pakgopay.mapper.dto.UserDTO;
import com.pakgopay.util.CommonUtil;
import com.pakgopay.util.CloudflareIpWhitelistUtil;
import com.pakgopay.util.SnowflakeIdService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.Set;

@Slf4j
@Service
public class UserService {
    @Autowired
    private UserMapper userMapper;

    @Autowired
    private BalanceMapper balanceMapper;

    @Autowired
    private SnowflakeIdService snowflakeIdService;
    @Autowired
    private CloudflareIpWhitelistUtil cloudflareIpWhitelistUtil;

    /*public List<UserDTO> listUsers() {
        return userMapper.selectAllUser();
    }*/

    public CommonResponse createLoginUser(CreateUserRequest user) {


        try {
            createUser(user);
            return CommonResponse.success("Create user successfully");
        } catch (PakGoPayException e) {
            log.error("createLoginUser failed, code: {} message: {}", e.getErrorCode(), e.getMessage());
            return CommonResponse.fail(e.getCode(), "createLoginUser failed: " + e.getMessage());
        }
    }

    public long createUser(CreateUserRequest user) throws PakGoPayException {
        // check data
        if (!user.getPassword().equals(user.getConfirmPassword())) {
            throw new PakGoPayException(ResultCode.FAIL, "check password is same with confirm password");
        }

        if (user.getOperatorId() == null) {
            throw new PakGoPayException(ResultCode.FAIL, "operator is invalid");
        }

        UserDTO operator = userMapper.getOneUserByUserId(user.getOperatorId());

        UserDTO checkUser = userMapper.getOneUser(user.getLoginName());
        if (checkUser != null) {
            throw new PakGoPayException(ResultCode.FAIL, "user already exist");
        }
        //verify google code TODO 转移至filter
//        boolean googleResult = GoogleUtil.verifyQrCode(operator.getLoginSecret(), user.getGoogleCode());
//
//        if (!googleResult) {
//            throw new PakGoPayException(ResultCode.FAIL, "check operator google code failed!");
//        }
        // build userId
        long userId = snowflakeIdService.nextId();
        UserDTO newUser = new UserDTO();
        newUser.setUserId(String.valueOf(userId));
        newUser.setPassword(user.getPassword());
        newUser.setStatus(user.getStatus());
        newUser.setLoginName(user.getLoginName());
        newUser.setRoleId(user.getRoleId());
        newUser.setLoginIps(user.getLoginIps());
        newUser.setWithdrawalIps(user.getWithdrawalIps());
        newUser.setContactName(user.getContactName());
        newUser.setContactEmail(user.getContactEmail());
        newUser.setContactPhone(user.getContactPhone());
        int result = 0;
        /*try {*/
        result = userMapper.createUser(newUser);
        /*} catch (Exception e){
            if (e.getMessage().contains("Duplicate entry")) {
                return CommonResponse.fail(ResultCode.FAIL, "login name is exist");
            }
        }*/

        if (result == 0) {
            throw new PakGoPayException(ResultCode.FAIL, "create user failed");
        }

        syncUserWhitelistToCloudflare(newUser);

        return userId;
    }

    private void syncUserWhitelistToCloudflare(UserDTO user) {
        try {
            Set<String> ips = new LinkedHashSet<>();
            ips.addAll(CommonUtil.parseIpWhitelist(user.getLoginIps()));
            ips.addAll(CommonUtil.parseIpWhitelist(user.getWithdrawalIps()));
            if (ips.isEmpty()) {
                log.info("skip sync user whitelist to cloudflare, userId={}, no ips configured",
                        user == null ? null : user.getUserId());
                return;
            }
            log.info("start sync user whitelist to cloudflare, userId={}, ipCount={}, ips={}",
                    user.getUserId(), ips.size(), ips);
            cloudflareIpWhitelistUtil.addIps(ips, "user-create:" + user.getUserId());
            log.info("finish sync user whitelist to cloudflare, userId={}, ipCount={}",
                    user.getUserId(), ips.size());
        } catch (Exception e) {
            // DB write already succeeded; keep API successful and record sync failure.
            log.warn("sync user whitelist to cloudflare failed, userId={}, message={}",
                    user == null ? null : user.getUserId(), e.getMessage());
        }
    }

    public void validateWithdrawalPermission(String merchantAgentId, String clientIp) {
        if (merchantAgentId == null || merchantAgentId.isBlank()) {
            throw new PakGoPayException(ResultCode.USER_IS_NOT_EXIST);
        }
        if (clientIp == null || clientIp.isBlank()) {
            throw new PakGoPayException(ResultCode.IS_NOT_WHITE_IP, "client ip is empty");
        }
        UserDTO user = userMapper.getOneUserByUserId(merchantAgentId);
        if (user == null) {
            throw new PakGoPayException(ResultCode.USER_IS_NOT_EXIST);
        }
        if (!CommonUtil.parseIpWhitelist(user.getWithdrawalIps()).contains(clientIp)) {
            throw new PakGoPayException(ResultCode.IS_NOT_WHITE_IP);
        }
    }
}
