package com.pakgopay.controller;

import com.pakgopay.common.constant.CommonConstant;
import com.pakgopay.common.enums.ResultCode;
import com.pakgopay.data.reqeust.CreateUserRequest;
import com.pakgopay.data.reqeust.roleManagement.AddRoleRequest;
import com.pakgopay.data.reqeust.roleManagement.DeleteRoleRequest;
import com.pakgopay.data.reqeust.roleManagement.ModifyRoleRequest;
import com.pakgopay.data.reqeust.systemConfig.LoginUserRequest;
import com.pakgopay.data.reqeust.systemConfig.RateLimitConfigRequest;
import com.pakgopay.data.reqeust.systemConfig.TelegramConfigRequest;
import com.pakgopay.data.response.CommonResponse;
import com.pakgopay.service.common.TelegramService;
import com.pakgopay.service.common.RateLimitConfigService;
import com.pakgopay.service.SystemConfigService;
import com.pakgopay.service.impl.UserService;
import com.pakgopay.thirdUtil.GoogleUtil;
import com.pakgopay.thirdUtil.RedisUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.ibatis.annotations.Param;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

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
    private TelegramService telegramService;
    @Autowired
    private RateLimitConfigService rateLimitConfigService;

    @PostMapping("/createUser")
    public CommonResponse createLoginUser(@RequestBody CreateUserRequest createUserRequest){
        return userService.createLoginUser(createUserRequest);
    }


    @GetMapping("/roleList")
    public CommonResponse roleList(@RequestParam(required = false) String roleName){
        return systemConfigService.listRoles(roleName);
    }

    @PostMapping("/loginUserList")
    public CommonResponse loginUserList(@RequestBody(required = false) LoginUserRequest loginUserRequest) {
        return systemConfigService.listLoginUsers(loginUserRequest);
    }

    @GetMapping("/manageLoginUserStatus")
    public CommonResponse manageLoginUserStatus(String userId, Integer status, Integer googleCode, String operatorId){
        return systemConfigService.updateLoginUserStatus(userId, status, googleCode, operatorId);
    }

    @GetMapping("/deleteLoginUser")
    public CommonResponse deleteLoginUser(String userId, Integer googleCode, String operatorId){
        return systemConfigService.deleteLoginUser(userId, googleCode, operatorId);
    }

    @GetMapping("/loginUserByLoginName")
    public CommonResponse loginUserByLoginName(String loginName){
        return systemConfigService.fetchLoginUserByLoginName(loginName);
    }

    @PostMapping("/addRole")
    public CommonResponse addRoleInfo(@RequestBody AddRoleRequest addRoleRequest, HttpServletRequest request){
        return systemConfigService.createRole(addRoleRequest, request);
    }

    @PostMapping("/modifyRoleInfo")
    public CommonResponse modifyRoleInfo(@RequestBody ModifyRoleRequest modifyRoleRequest, HttpServletRequest request){
        return systemConfigService.updateRole(modifyRoleRequest, request);
    }

    @PostMapping("/deleteRole")
    public CommonResponse deleteRole(@RequestBody DeleteRoleRequest deleteRoleRequest, HttpServletRequest request){
        return systemConfigService.deleteRole(deleteRoleRequest, request);
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
        return systemConfigService.resetGoogleKey(operator, userId, loginName);
    }

    @GetMapping("/bindGoogleKey")
    public CommonResponse bindGoogleKey(HttpServletRequest request, @Param("userId") String userId, @Param("loginName") String loginName){
        String userInfo = GoogleUtil.getUserInfoFromToken(request);
        String operator = userInfo.split("&")[0];
        return systemConfigService.resetGoogleKey(operator, userId, loginName);
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

    @GetMapping("/telegramConfig")
    public CommonResponse getTelegramConfig() {
        return CommonResponse.success(telegramService.getTelegramConfig());
    }

    @PostMapping("/telegramConfig")
    public CommonResponse updateTelegramConfig(@RequestBody TelegramConfigRequest request) {
        telegramService.updateTelegramConfig(
            request.getToken(),
            request.getChatId(),
            request.getWebhookSecret(),
            request.getAllowedUserIds(),
            request.getEnabled()
        );
        return CommonResponse.success("ok");
    }

    @GetMapping("/rateLimitConfig")
    public CommonResponse getRateLimitConfig() {
        return CommonResponse.success(rateLimitConfigService.getConfigAsMap());
    }

    @PostMapping("/rateLimitConfig")
    public CommonResponse updateRateLimitConfig(@RequestBody RateLimitConfigRequest request) {
        if (Boolean.TRUE.equals(request.getEnabled())) {
            Long windowSeconds = request.getWindowSeconds();
            Long maxRequests = request.getMaxRequests();
            if (windowSeconds == null || windowSeconds <= 0 || maxRequests == null || maxRequests <= 0) {
                return CommonResponse.fail(ResultCode.INVALID_PARAMS, "windowSeconds/maxRequests must be greater than 0");
            }
        }
        rateLimitConfigService.updateConfig(
            request.getEnabled(),
            request.getWindowSeconds(),
            request.getMaxRequests(),
            request.getFixedIpQps()
        );
        return CommonResponse.success("ok");
    }
}
