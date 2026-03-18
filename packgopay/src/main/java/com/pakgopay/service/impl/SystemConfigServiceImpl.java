package com.pakgopay.service.impl;

import com.alibaba.excel.EasyExcel;
import com.pakgopay.common.constant.CommonConstant;
import com.pakgopay.common.enums.ResultCode;
import com.pakgopay.common.enums.SyncTypeEnum;
import com.pakgopay.common.exception.PakGoPayException;
import com.pakgopay.data.reqeust.roleManagement.AddRoleRequest;
import com.pakgopay.data.reqeust.roleManagement.DeleteRoleRequest;
import com.pakgopay.data.reqeust.roleManagement.ModifyRoleRequest;
import com.pakgopay.data.reqeust.systemConfig.SystemSyncRequest;
import com.pakgopay.data.reqeust.systemConfig.LoginLogQueryRequest;
import com.pakgopay.data.reqeust.systemConfig.LoginUserRequest;
import com.pakgopay.data.reqeust.systemConfig.OperateLogQueryRequest;
import com.pakgopay.data.response.CommonResponse;
import com.pakgopay.data.response.RoleInfoResponse;
import com.pakgopay.data.response.roleManagement.RoleMenuInfoResponse;
import com.pakgopay.data.response.systemConfig.LoginLogQueryResponse;
import com.pakgopay.data.response.systemConfig.LoginUserResponse;
import com.pakgopay.data.response.systemConfig.OperateLogQueryResponse;
import com.pakgopay.data.response.systemConfig.ResetGoogleKeyResponse;
import com.pakgopay.mapper.LoginLogMapper;
import com.pakgopay.mapper.OperateLogMapper;
import com.pakgopay.mapper.RoleMapper;
import com.pakgopay.mapper.RoleMenuMapper;
import com.pakgopay.mapper.UserMapper;
import com.pakgopay.mapper.dto.CurrencyTypeSyncExcelRow;
import com.pakgopay.mapper.dto.BankCodeSyncExcelRow;
import com.pakgopay.mapper.dto.Role;
import com.pakgopay.mapper.dto.RoleMenuDTO;
import com.pakgopay.mapper.dto.UserDTO;
import com.pakgopay.service.common.LoginLogService;
import com.pakgopay.service.common.UserStatusService;
import com.pakgopay.service.BankCodeService;
import com.pakgopay.service.CurrencyTypeManagementService;
import com.pakgopay.service.SystemConfigService;
import com.pakgopay.thirdUtil.GoogleUtil;
import com.pakgopay.thirdUtil.RedisUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.net.URI;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class SystemConfigServiceImpl implements SystemConfigService {

    @Autowired
    private RoleMapper roleMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private LoginLogMapper loginLogMapper;

    @Autowired
    private OperateLogMapper operateLogMapper;

    @Autowired
    private RoleMenuMapper roleMenuMapper;

    @Autowired
    private RedisUtil redisUtil;

    @Autowired
    private LoginLogService loginLogService;

    @Autowired
    private UserStatusService userStatusService;

    @Autowired
    private CurrencyTypeManagementService currencyTypeManagementService;

    @Autowired
    private BankCodeService bankCodeService;

    @Value("${pakgopay.currency-sync.excel-url}")
    private String currencySyncExcelUrl;

    @Value("${pakgopay.bankCode-sync.excel-url}")
    private String bankCodeSyncExcelUrl;

    @Override
    public CommonResponse listRoles(String roleName) {
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
    public CommonResponse listLoginUsers(LoginUserRequest loginUserRequest) {
        if (loginUserRequest == null) {
            loginUserRequest = new LoginUserRequest();
        }
        if (loginUserRequest.getPageNo() == null) {
            loginUserRequest.setPageNo(1);
        }
        if (loginUserRequest.getPageSize() == null) {
            loginUserRequest.setPageSize(10);
        }
        Integer count = userMapper.selectAllUserCount(loginUserRequest);
        List<UserDTO> loginUsers = userMapper.selectAllUser(loginUserRequest);
        if (loginUsers != null) {
            loginUsers.forEach(user -> {
                String loginSecret = user == null ? null : user.getLoginSecret();
                boolean isBind = loginSecret != null && !loginSecret.trim().isEmpty();
                if (user != null) {
                    user.setIsBind(isBind);
                }
            });
        }
        LoginUserResponse loginUserResponse = new LoginUserResponse();
        loginUserResponse.setLoginUsers(loginUsers);
        loginUserResponse.setTotalNumber(count);
        loginUserResponse.setPageNo(loginUserRequest.getPageNo());
        loginUserResponse.setPageSize(loginUserRequest.getPageSize());
        if (loginUsers.isEmpty()) {
            return CommonResponse.success(null);
        }
        return CommonResponse.success(loginUserResponse);
    }

    @Override
    public CommonResponse listLoginLogs(LoginLogQueryRequest loginLogQueryRequest) {
        if (loginLogQueryRequest == null) {
            loginLogQueryRequest = new LoginLogQueryRequest();
        }
        if (loginLogQueryRequest.getPageNo() == null) {
            loginLogQueryRequest.setPageNo(1);
        }
        if (loginLogQueryRequest.getPageSize() == null) {
            loginLogQueryRequest.setPageSize(10);
        }
        Integer count = loginLogMapper.countByQuery(loginLogQueryRequest);
        LoginLogQueryResponse response = new LoginLogQueryResponse();
        response.setTotalNumber(count);
        response.setPageNo(loginLogQueryRequest.getPageNo());
        response.setPageSize(loginLogQueryRequest.getPageSize());
        if (count == null || count == 0) {
            response.setLoginLogs(new ArrayList<>());
            return CommonResponse.success(response);
        }
        response.setLoginLogs(loginLogMapper.pageByQuery(loginLogQueryRequest));
        return CommonResponse.success(response);
    }

    @Override
    public CommonResponse listOperateLogs(OperateLogQueryRequest operateLogQueryRequest) {
        if (operateLogQueryRequest == null) {
            operateLogQueryRequest = new OperateLogQueryRequest();
        }
        if (operateLogQueryRequest.getPageNo() == null) {
            operateLogQueryRequest.setPageNo(1);
        }
        if (operateLogQueryRequest.getPageSize() == null) {
            operateLogQueryRequest.setPageSize(10);
        }
        Integer count = operateLogMapper.countByQuery(operateLogQueryRequest);
        OperateLogQueryResponse response = new OperateLogQueryResponse();
        response.setTotalNumber(count);
        response.setPageNo(operateLogQueryRequest.getPageNo());
        response.setPageSize(operateLogQueryRequest.getPageSize());
        if (count == null || count == 0) {
            response.setOperateLogs(new ArrayList<>());
            return CommonResponse.success(response);
        }
        response.setOperateLogs(operateLogMapper.pageByQuery(operateLogQueryRequest));
        return CommonResponse.success(response);
    }

    @Override
    public CommonResponse updateLoginUserStatus(String userId, Integer status, String operatorId) {
        int stopResult = userMapper.stopLoginUser(userId, status);
        if (stopResult == 0) {
            return CommonResponse.fail(ResultCode.FAIL, "stop login user failed");
        }
        userStatusService.applyStatusUpdate(userId, status);
        if (Integer.valueOf(0).equals(status)) {
            loginLogService.writeKickedLogout(userId, operatorId);
        }
        return CommonResponse.success(ResultCode.SUCCESS);
    }

    @Override
    public CommonResponse deleteLoginUser(String userId, String operatorId) {
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
    public CommonResponse fetchLoginUserByLoginName(String loginName) {
        UserDTO userDTO = userMapper.loginUserByLoginName(loginName);
        return CommonResponse.success(userDTO);
    }

    @Override
    @Transactional
    public CommonResponse createRole(AddRoleRequest addRoleRequest, HttpServletRequest request) {
        try {
            String operatorName = resolveOperatorNameFromRequest(request);
            if (!StringUtils.hasText(operatorName)) {
                return CommonResponse.fail(ResultCode.INVALID_TOKEN, ResultCode.INVALID_TOKEN.getMessage());
            }
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
    public CommonResponse fetchRoleMenuByRoleId(Integer menuId) {

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
           if (roleMenuDTO.getMenuId() != null && roleMenuDTO.getMenuId().length() > 3) {
               menuList.add(roleMenuDTO.getMenuId());
           }

        });
        response.setMenuList(menuList);
        return CommonResponse.success(response);
    }

    @Override
    @Transactional
    public CommonResponse updateRole(ModifyRoleRequest modifyRoleRequest, HttpServletRequest request) {
        try {
            String operatorName = resolveOperatorNameFromRequest(request);
            if (!StringUtils.hasText(operatorName)) {
                return CommonResponse.fail(ResultCode.INVALID_TOKEN, ResultCode.INVALID_TOKEN.getMessage());
            }
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

    private String resolveOperatorNameFromRequest(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        return (String) request.getAttribute(CommonConstant.ATTR_USER_NAME);
    }

    @Override
    @Transactional
    public CommonResponse deleteRole(DeleteRoleRequest deleteRoleRequest, HttpServletRequest request) {
        try {
            String operatorName = resolveOperatorNameFromRequest(request);
            if (!StringUtils.hasText(operatorName)) {
                return CommonResponse.fail(ResultCode.INVALID_TOKEN, ResultCode.INVALID_TOKEN.getMessage());
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

    @Override
    public CommonResponse resetGoogleKey(String operator, String userId, String loginName) {
        log.info("start to reset google key for userId={}, loginName={}", userId, loginName);
        return resetKey(userId, loginName);
    }

    @Override
    public CommonResponse syncSystemData(SystemSyncRequest request) {
        SyncTypeEnum syncType = SyncTypeEnum.fromType(request.getSyncType());
        if (syncType == null) {
            return CommonResponse.fail(ResultCode.ORDER_PARAM_VALID, "invalid syncType");
        }
        try {
            if (syncType == SyncTypeEnum.CURRENCY) {
                List<CurrencyTypeSyncExcelRow> rows;
                try (InputStream inputStream = URI.create(currencySyncExcelUrl).toURL().openStream()) {
                    rows = EasyExcel.read(inputStream)
                            .head(CurrencyTypeSyncExcelRow.class)
                            .headRowNumber(2)
                            .sheet()
                            .doReadSync();
                }
                return currencyTypeManagementService.syncCurrencyTypesFromRows(
                        rows,
                        currencySyncExcelUrl,
                        request.getUserId(),
                        request.getUserName());
            }
            List<BankCodeSyncExcelRow> rows;
            try (InputStream inputStream = URI.create(bankCodeSyncExcelUrl).toURL().openStream()) {
                rows = EasyExcel.read(inputStream)
                        .head(BankCodeSyncExcelRow.class)
                        .headRowNumber(2)
                        .sheet()
                        .doReadSync();
            }
            return bankCodeService.syncBankCodesFromRows(
                    rows,
                    bankCodeSyncExcelUrl,
                    request.getUserId(),
                    request.getUserName());
        } catch (Exception e) {
            log.error("sync system data failed", e);
            return CommonResponse.fail(ResultCode.FAIL, "sync system data failed " + e.getMessage());
        }
    }

    public CommonResponse resetKey(String userId, String userName) {
        String generateKey = GoogleUtil.getSecretKey();
        log.info("start to update google key of database");
        int result = userMapper.resetSecretkey(generateKey, userId);
        if (result == 0) {
            return new CommonResponse(ResultCode.BIND_SECRET_KEY_FAIL);
        }
        String qrCode = GoogleUtil.getQrCode(generateKey, userName);
        ResetGoogleKeyResponse response = new ResetGoogleKeyResponse();
        // 清除无密钥登陆次数
        redisUtil.remove(CommonConstant.USER_NO_KEY_LOGIN_TIMES+userName);
        response.setQrCode(qrCode);
        response.setSecretKey(generateKey);
        return CommonResponse.success(response);
    }
}
