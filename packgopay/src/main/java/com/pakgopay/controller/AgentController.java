package com.pakgopay.controller;

import com.pakgopay.common.enums.ResultCode;
import com.pakgopay.common.exception.PakGoPayException;
import com.pakgopay.data.reqeust.account.AccountAddRequest;
import com.pakgopay.data.reqeust.account.AccountEditRequest;
import com.pakgopay.data.reqeust.account.AccountQueryRequest;
import com.pakgopay.data.reqeust.agent.*;
import com.pakgopay.data.response.CommonResponse;
import com.pakgopay.service.AgentService;
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

    @PostMapping("/queryAgent")
    public CommonResponse queryAgent(@RequestBody @Valid AgentQueryRequest agentQueryRequest, HttpServletRequest request) {
        log.info("queryAgent start");
        try {
            return agentService.queryAgent(agentQueryRequest);
        } catch (PakGoPayException e) {
            log.error("queryAgent failed, code: {} message: {}", e.getErrorCode(), e.getMessage());
            return CommonResponse.fail(e.getCode(), "queryAgent failed: " + e.getMessage());
        }
    }

    @PostMapping(value = "exportAgent")
    public void exportAgent(
            @RequestBody @Valid AgentQueryRequest agentQueryRequest, HttpServletResponse response) {
        log.info("exportAgent start");
        try {
            agentService.exportAgent(agentQueryRequest, response);
        } catch (PakGoPayException e) {
            log.error("exportAgent failed, code: {} message: {}", e.getErrorCode(), e.getMessage());
            ExportFileUtils.writeJsonError(response, e.getCode(), "exportAgent failed: " + e.getMessage());
        } catch (IOException e) {
            log.error("exportAgent failed, IOException message: {}", e.getMessage());
            ExportFileUtils.writeJsonError(response,
                    ResultCode.FAIL, "exportAgent failed, IOException message: " + e.getMessage());
        }
        log.info("exportAgent end");
    }

    @PostMapping("/editAgent")
    public CommonResponse editAgent(@RequestBody @Valid AgentEditRequest agentEditRequest, HttpServletRequest request) {
        log.info("editAgent start");
        try {
            return agentService.editAgent(agentEditRequest);
        } catch (PakGoPayException e) {
            log.error("editAgent failed, code: {} message: {}", e.getErrorCode(), e.getMessage());
            return CommonResponse.fail(e.getCode(), "editAgent failed: " + e.getMessage());
        }
    }

    @PostMapping("/addAgent")
    public CommonResponse addAgent(@RequestBody @Valid AgentAddRequest agentAddRequest, HttpServletRequest request) {
        log.info("addAgent start");
        try {
            return agentService.addAgent(agentAddRequest);
        } catch (PakGoPayException e) {
            log.error("addAgent failed, code: {} message: {}", e.getErrorCode(), e.getMessage());
            return CommonResponse.fail(e.getCode(), "addAgent failed: " + e.getMessage());
        }
    }

    @PostMapping("/queryAgentAccount")
    public CommonResponse queryAgentAccount(@RequestBody @Valid AccountQueryRequest agentQueryRequest, HttpServletRequest request) {
        log.info("queryAgentAccount start");
        try {
            return agentService.queryAgentAccount(agentQueryRequest);
        } catch (PakGoPayException e) {
            log.error("queryAgentAccount failed, code: {} message: {}", e.getErrorCode(), e.getMessage());
            return CommonResponse.fail(e.getCode(), "queryAgentAccount failed: " + e.getMessage());
        }
    }

    @PostMapping(value = "exportAgentAccount")
    public void exportAgentAccount(
            @RequestBody @Valid AccountQueryRequest agentQueryRequest, HttpServletResponse response) {
        log.info("exportAgentAccount start");
        try {
            agentService.exportAgentAccount(agentQueryRequest, response);
        } catch (PakGoPayException e) {
            log.error("exportAgentAccount failed, code: {} message: {}", e.getErrorCode(), e.getMessage());
            ExportFileUtils.writeJsonError(response, e.getCode(), "exportAgentAccount failed: " + e.getMessage());
        } catch (IOException e) {
            log.error("exportAgentAccount failed, IOException message: {}", e.getMessage());
            ExportFileUtils.writeJsonError(response,
                    ResultCode.FAIL, "exportAgentAccount failed, IOException message: " + e.getMessage());
        }
        log.info("exportAgentAccount end");
    }

    @PostMapping("/editAgentAccount")
    public CommonResponse editAgentAccount(@RequestBody @Valid AccountEditRequest accountEditRequest, HttpServletRequest request) {
        log.info("editAgentAccount start");
        try {
            return agentService.editAgentAccount(accountEditRequest);
        } catch (PakGoPayException e) {
            log.error("editAgentAccount failed, code: {} message: {}", e.getErrorCode(), e.getMessage());
            return CommonResponse.fail(e.getCode(), "editAgentAccount failed: " + e.getMessage());
        }
    }

    @PostMapping("/addAgentAccount")
    public CommonResponse addAgentAccount(@RequestBody @Valid AccountAddRequest accountAddRequest, HttpServletRequest request) {
        log.info("addAgentAccount start");
        try {
            return agentService.addAgentAccount(accountAddRequest);
        } catch (PakGoPayException e) {
            log.error("addAgentAccount failed, code: {} message: {}", e.getErrorCode(), e.getMessage());
            return CommonResponse.fail(e.getCode(), "addAgentAccount failed: " + e.getMessage());
        }
    }
}
