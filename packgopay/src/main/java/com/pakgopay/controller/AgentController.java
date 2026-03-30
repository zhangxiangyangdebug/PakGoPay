package com.pakgopay.controller;

import com.pakgopay.common.enums.OperateInterfaceEnum;
import com.pakgopay.common.enums.ResultCode;
import com.pakgopay.common.exception.PakGoPayException;
import com.pakgopay.common.log.LogLevelPolicy;
import com.pakgopay.data.reqeust.account.AccountAddRequest;
import com.pakgopay.data.reqeust.account.AccountEditRequest;
import com.pakgopay.data.reqeust.account.AccountQueryRequest;
import com.pakgopay.data.reqeust.agent.*;
import com.pakgopay.data.response.CommonResponse;
import com.pakgopay.service.AgentService;
import com.pakgopay.service.common.OperateLogService;
import com.pakgopay.util.ExportFileUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@Slf4j
@RestController
@RequestMapping("/pakGoPay/server")
public class AgentController {
    @Autowired
    private AgentService agentService;

    @Autowired
    private OperateLogService operateLogService;

    @PostMapping("/queryAgent")
    public CommonResponse queryAgent(@RequestBody @Valid AgentQueryRequest agentQueryRequest, HttpServletRequest request) {
        try {
            return agentService.queryAgents(agentQueryRequest);
        } catch (PakGoPayException e) {
            LogLevelPolicy.logBizException(log, "queryAgent failed", e);
            return CommonResponse.fail(e.getCode(), "queryAgent failed: " + e.getMessage());
        }
    }

    @PostMapping(value = "exportAgent")
    public void exportAgent(
            @RequestBody @Valid AgentQueryRequest agentQueryRequest, HttpServletResponse response) {
        try {
            agentService.exportAgents(agentQueryRequest, response);
        } catch (PakGoPayException e) {
            LogLevelPolicy.logBizException(log, "exportAgent failed", e);
            ExportFileUtils.writeJsonError(response, e.getCode(), "exportAgent failed: " + e.getMessage());
        } catch (IOException e) {
            log.error("exportAgent failed, IOException message: {}", e.getMessage());
            ExportFileUtils.writeJsonError(response,
                    ResultCode.FAIL, "exportAgent failed, IOException message: " + e.getMessage());
        }
    }

    @PostMapping("/editAgent")
    public CommonResponse editAgent(@RequestBody @Valid AgentEditRequest agentEditRequest, HttpServletRequest request) {
        try {
            CommonResponse response = agentService.updateAgent(agentEditRequest);
            operateLogService.write(OperateInterfaceEnum.EDIT_AGENT, agentEditRequest.getUserId(), agentEditRequest);
            return response;
        } catch (PakGoPayException e) {
            LogLevelPolicy.logBizException(log, "editAgent failed", e);
            return CommonResponse.fail(e.getCode(), "editAgent failed: " + e.getMessage());
        }
    }

    @PostMapping("/addAgent")
    public CommonResponse addAgent(@RequestBody @Valid AgentAddRequest agentAddRequest, HttpServletRequest request) {
        try {
            CommonResponse response = agentService.createAgent(agentAddRequest);
            operateLogService.write(OperateInterfaceEnum.ADD_AGENT, agentAddRequest.getUserId(), agentAddRequest);
            return response;
        } catch (PakGoPayException e) {
            LogLevelPolicy.logBizException(log, "addAgent failed", e);
            return CommonResponse.fail(e.getCode(), "addAgent failed: " + e.getMessage());
        }
    }

    @PostMapping("/queryAgentAccount")
    public CommonResponse queryAgentAccount(@RequestBody @Valid AccountQueryRequest agentQueryRequest, HttpServletRequest request) {
        try {
            return agentService.queryAgentAccounts(agentQueryRequest);
        } catch (PakGoPayException e) {
            LogLevelPolicy.logBizException(log, "queryAgentAccount failed", e);
            return CommonResponse.fail(e.getCode(), "queryAgentAccount failed: " + e.getMessage());
        }
    }

    @PostMapping(value = "exportAgentAccount")
    public void exportAgentAccount(
            @RequestBody @Valid AccountQueryRequest agentQueryRequest, HttpServletResponse response) {
        try {
            agentService.exportAgentAccounts(agentQueryRequest, response);
        } catch (PakGoPayException e) {
            LogLevelPolicy.logBizException(log, "exportAgentAccount failed", e);
            ExportFileUtils.writeJsonError(response, e.getCode(), "exportAgentAccount failed: " + e.getMessage());
        } catch (IOException e) {
            log.error("exportAgentAccount failed, IOException message: {}", e.getMessage());
            ExportFileUtils.writeJsonError(response,
                    ResultCode.FAIL, "exportAgentAccount failed, IOException message: " + e.getMessage());
        }
    }

    @PostMapping("/editAgentAccount")
    public CommonResponse editAgentAccount(@RequestBody @Valid AccountEditRequest accountEditRequest, HttpServletRequest request) {
        try {
            CommonResponse response = agentService.updateAgentAccount(accountEditRequest);
            operateLogService.write(OperateInterfaceEnum.EDIT_AGENT_ACCOUNT, accountEditRequest.getUserId(), accountEditRequest);
            return response;
        } catch (PakGoPayException e) {
            LogLevelPolicy.logBizException(log, "editAgentAccount failed", e);
            return CommonResponse.fail(e.getCode(), "editAgentAccount failed: " + e.getMessage());
        }
    }

    @PostMapping("/addAgentAccount")
    public CommonResponse addAgentAccount(@RequestBody @Valid AccountAddRequest accountAddRequest, HttpServletRequest request) {
        try {
            CommonResponse response = agentService.createAgentAccount(accountAddRequest);
            operateLogService.write(OperateInterfaceEnum.ADD_AGENT_ACCOUNT, accountAddRequest.getUserId(), accountAddRequest);
            return response;
        } catch (PakGoPayException e) {
            LogLevelPolicy.logBizException(log, "addAgentAccount failed", e);
            return CommonResponse.fail(e.getCode(), "addAgentAccount failed: " + e.getMessage());
        }
    }
}
