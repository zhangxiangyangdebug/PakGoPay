package com.pakgopay.controller;

import com.pakgopay.common.enums.ResultCode;
import com.pakgopay.common.exception.PakGoPayException;
import com.pakgopay.common.reqeust.agent.AgentAddRequest;
import com.pakgopay.common.reqeust.agent.AgentEditRequest;
import com.pakgopay.common.reqeust.agent.AgentQueryRequest;
import com.pakgopay.common.response.CommonResponse;
import com.pakgopay.service.agent.AgentService;
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
}
