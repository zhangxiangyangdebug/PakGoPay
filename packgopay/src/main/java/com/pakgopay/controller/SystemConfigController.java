package com.pakgopay.controller;

import com.pakgopay.common.constant.CommonConstant;
import com.pakgopay.common.enums.OperateInterfaceEnum;
import com.pakgopay.common.enums.ResultCode;
import com.pakgopay.common.enums.SystemConfigGroupEnum;
import com.pakgopay.data.reqeust.CreateUserRequest;
import com.pakgopay.data.reqeust.roleManagement.AddRoleRequest;
import com.pakgopay.data.reqeust.roleManagement.DeleteRoleRequest;
import com.pakgopay.data.reqeust.roleManagement.ModifyRoleRequest;
import com.pakgopay.data.reqeust.systemConfig.EditUserRequest;
import com.pakgopay.data.reqeust.systemConfig.LoginLogQueryRequest;
import com.pakgopay.data.reqeust.systemConfig.LoginUserRequest;
import com.pakgopay.data.reqeust.systemConfig.OperateLogQueryRequest;
import com.pakgopay.data.reqeust.systemConfig.SystemConfigGroupUpdateRequest;
import com.pakgopay.data.reqeust.systemConfig.TelegramBroadcastRequest;
import com.pakgopay.data.response.CommonResponse;
import com.pakgopay.mapper.UserMapper;
import com.pakgopay.mapper.dto.UserDTO;
import com.pakgopay.service.common.OperateLogService;
import com.pakgopay.service.common.SystemConfigGroupService;
import com.pakgopay.service.common.TelegramService;
import com.pakgopay.service.SystemConfigService;
import com.pakgopay.service.impl.UserService;
import com.pakgopay.thirdUtil.GoogleUtil;
import com.pakgopay.thirdUtil.RedisUtil;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.apache.ibatis.annotations.Param;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/pakGoPay/server/SystemConfig")
public class SystemConfigController {

    @Autowired
    private UserService userService;

    @Autowired
    private SystemConfigService systemConfigService;
    @Autowired
    private RedisUtil redisUtil;
    @Autowired
    private SystemConfigGroupService systemConfigGroupService;

    @Autowired
    private OperateLogService operateLogService;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private TelegramService telegramService;

    @PostMapping("/createUser")
    public CommonResponse createLoginUser(@RequestBody CreateUserRequest createUserRequest){
        CommonResponse response = userService.createLoginUser(createUserRequest);
        operateLogService.write(OperateInterfaceEnum.CREATE_USER, createUserRequest.getOperatorId(), createUserRequest);
        return response;
    }

    @PostMapping("/editUser")
    public CommonResponse editLoginUser(
            @RequestBody @Valid EditUserRequest editUserRequest,
            @RequestAttribute(CommonConstant.ATTR_USER_ID) String operatorId){
        editUserRequest.setOperatorId(operatorId);
        CommonResponse response = userService.editLoginUser(editUserRequest);
        operateLogService.write(OperateInterfaceEnum.EDIT_LOGIN_USER, operatorId, editUserRequest);
        return response;
    }


    @GetMapping("/roleList")
    public CommonResponse roleList(@RequestParam(required = false) String roleName){
        return systemConfigService.listRoles(roleName);
    }

    @PostMapping("/loginUserList")
    public CommonResponse loginUserList(@RequestBody(required = false) LoginUserRequest loginUserRequest) {
        return systemConfigService.listLoginUsers(loginUserRequest);
    }

    @PostMapping("/loginLogList")
    public CommonResponse loginLogList(@RequestBody(required = false) @Valid LoginLogQueryRequest loginLogQueryRequest) {
        return systemConfigService.listLoginLogs(loginLogQueryRequest);
    }

    @PostMapping("/operateLogList")
    public CommonResponse operateLogList(@RequestBody(required = false) @Valid OperateLogQueryRequest operateLogQueryRequest) {
        return systemConfigService.listOperateLogs(operateLogQueryRequest);
    }

    @GetMapping("/manageLoginUserStatus")
    public CommonResponse manageLoginUserStatus(
            String userId,
            Integer status,
            @RequestAttribute(CommonConstant.ATTR_USER_ID) String operatorId){
        CommonResponse response = systemConfigService.updateLoginUserStatus(userId, status, operatorId);
        operateLogService.write(OperateInterfaceEnum.MANAGE_LOGIN_USER_STATUS, operatorId,
                new ManageLoginUserStatusPayload(userId, status));
        return response;
    }

    @GetMapping("/deleteLoginUser")
    public CommonResponse deleteLoginUser(
            String userId,
            @RequestAttribute(CommonConstant.ATTR_USER_ID) String operatorId){
        CommonResponse response = systemConfigService.deleteLoginUser(userId, operatorId);
        operateLogService.write(OperateInterfaceEnum.DELETE_LOGIN_USER, operatorId,
                new DeleteLoginUserPayload(userId));
        return response;
    }

    @GetMapping("/loginUserByLoginName")
    public CommonResponse loginUserByLoginName(String loginName){
        return systemConfigService.fetchLoginUserByLoginName(loginName);
    }

    @PostMapping("/addRole")
    public CommonResponse addRoleInfo(@RequestBody AddRoleRequest addRoleRequest, HttpServletRequest request){
        CommonResponse response = systemConfigService.createRole(addRoleRequest, request);
        operateLogService.write(OperateInterfaceEnum.ADD_ROLE, resolveOperatorUserIdFromRequest(request), addRoleRequest);
        return response;
    }

    @PostMapping("/modifyRoleInfo")
    public CommonResponse modifyRoleInfo(@RequestBody ModifyRoleRequest modifyRoleRequest, HttpServletRequest request){
        CommonResponse response = systemConfigService.updateRole(modifyRoleRequest, request);
        operateLogService.write(OperateInterfaceEnum.MODIFY_ROLE, resolveOperatorUserIdFromRequest(request), modifyRoleRequest);
        return response;
    }

    @PostMapping("/deleteRole")
    public CommonResponse deleteRole(@RequestBody DeleteRoleRequest deleteRoleRequest, HttpServletRequest request){
        CommonResponse response = systemConfigService.deleteRole(deleteRoleRequest, request);
        String operatorUserId = deleteRoleRequest.getUserId() == null
                ? resolveOperatorUserIdFromRequest(request)
                : deleteRoleRequest.getUserId();
        operateLogService.write(OperateInterfaceEnum.DELETE_ROLE, operatorUserId, deleteRoleRequest);
        return response;
    }

    @GetMapping("/getRoleInfoByRoleId")
    public CommonResponse getRoleInfoOfMenu(Integer roleId) {
        return systemConfigService.fetchRoleMenuByRoleId(roleId);
    }

    @GetMapping("/resetGoogleKey")
    public CommonResponse resetGoogleKey(HttpServletRequest request, @Param("userId") String userId, @Param("googleCode") Integer googleCode, @Param("loginName") String loginName){
        // 管理员重置令牌，校验谷歌验证码
        String userInfo = GoogleUtil.getUserInfoFromToken(request);
        String operator = userInfo.split("&")[0];
        CommonResponse response = systemConfigService.resetGoogleKey(operator, userId, loginName);
        operateLogService.write(OperateInterfaceEnum.RESET_GOOGLE_KEY, operator,
                new ResetGoogleKeyPayload(userId, loginName));
        return response;
    }

    @GetMapping("/bindGoogleKey")
    public CommonResponse bindGoogleKey(HttpServletRequest request, @Param("userId") String userId, @Param("loginName") String loginName){
        String userInfo = GoogleUtil.getUserInfoFromToken(request);
        String operator = userInfo.split("&")[0];
        CommonResponse response = systemConfigService.resetGoogleKey(operator, userId, loginName);
        operateLogService.write(OperateInterfaceEnum.BIND_GOOGLE_KEY, operator,
                new BindGoogleKeyPayload(userId, loginName));
        return response;
    }

    @GetMapping("/unCommonMessage")
    public CommonResponse getUncommonMessage(HttpServletRequest request, @Param("userName") String userName){
        String noKeyTimeInfo = redisUtil.getValue(CommonConstant.USER_NO_KEY_LOGIN_TIMES+userName);
        if (noKeyTimeInfo != null) {
            return CommonResponse.success(Integer.parseInt(noKeyTimeInfo));
        } else {
            return CommonResponse.success("success");
        }
    }

    @GetMapping("/config")
    public CommonResponse getSystemConfig(@RequestParam String group) {
        try {
            return CommonResponse.success(systemConfigGroupService.queryByGroup(group));
        } catch (IllegalArgumentException e) {
            return CommonResponse.fail(ResultCode.INVALID_PARAMS, e.getMessage());
        }
    }

    @PostMapping("/config")
    public CommonResponse updateSystemConfig(
            @RequestBody @Valid SystemConfigGroupUpdateRequest request,
            HttpServletRequest httpServletRequest) {
        try {
            String operatorUserId = resolveOperatorUserIdFromRequest(httpServletRequest);
            systemConfigGroupService.updateByGroup(request);
            SystemConfigGroupEnum group = SystemConfigGroupEnum.fromGroup(request.getGroup());
            operateLogService.write(group.getUpdateOperate(), operatorUserId, request);
            return CommonResponse.success("ok");
        } catch (IllegalArgumentException e) {
            return CommonResponse.fail(ResultCode.INVALID_PARAMS, e.getMessage());
        } catch (Exception e) {
            return CommonResponse.fail(ResultCode.FAIL, "update system config failed: " + e.getMessage());
        }
    }

    @PostMapping("/telegramBroadcast")
    public CommonResponse telegramBroadcast(
            @RequestBody @Valid TelegramBroadcastRequest request,
            HttpServletRequest httpServletRequest) {
        String operatorUserId = resolveOperatorUserIdFromRequest(httpServletRequest);
        try {
            if (!StringUtils.hasText(request.getContent()) && !StringUtils.hasText(request.getImageDataUrl())) {
                return CommonResponse.fail(ResultCode.INVALID_PARAMS, "content or image is required");
            }
            Set<String> chatIds = new LinkedHashSet<>();
            List<String> skippedAccounts = new ArrayList<>();
            List<String> invalidAccounts = new ArrayList<>();
            for (String account : request.getMerchantAccounts()) {
                if (account == null || account.isBlank()) {
                    continue;
                }
                UserDTO user = userMapper.loginUserByLoginName(account.trim());
                if (user == null || user.getRoleId() == null || user.getRoleId() != CommonConstant.ROLE_MERCHANT) {
                    invalidAccounts.add(account);
                    continue;
                }
                if (user.getTelegramGroup() == null || user.getTelegramGroup().isBlank()) {
                    skippedAccounts.add(account);
                    continue;
                }
                chatIds.add(user.getTelegramGroup().trim());
            }
            int sent = 0;
            boolean pinMessage = Boolean.TRUE.equals(request.getPinMessage());
            for (String chatId : chatIds) {
                telegramService.sendBroadcastContentTo(
                        chatId,
                        request.getTitle(),
                        request.getContent(),
                        request.getImageName(),
                        request.getImageDataUrl(),
                        pinMessage
                );
                sent++;
            }
            operateLogService.write(OperateInterfaceEnum.TELEGRAM_BROADCAST, operatorUserId, request);
            return CommonResponse.success(new TelegramBroadcastResult(
                    request.getMerchantAccounts() == null ? 0 : request.getMerchantAccounts().size(),
                    sent,
                    skippedAccounts,
                    invalidAccounts
            ));
        } catch (Exception e) {
            return CommonResponse.fail(ResultCode.FAIL, "telegram broadcast failed: " + e.getMessage());
        }
    }

    private String resolveOperatorUserIdFromRequest(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        Object userId = request.getAttribute(CommonConstant.ATTR_USER_ID);
        return userId == null ? null : String.valueOf(userId);
    }

    private record ManageLoginUserStatusPayload(String userId, Integer status) {}
    private record DeleteLoginUserPayload(String userId) {}
    private record ResetGoogleKeyPayload(String userId, String loginName) {}
    private record BindGoogleKeyPayload(String userId, String loginName) {}

    @Data
    @AllArgsConstructor
    private static class TelegramBroadcastResult {
        private Integer totalAccounts;
        private Integer sentGroupCount;
        private List<String> skippedUnboundAccounts;
        private List<String> invalidAccounts;
    }
}
