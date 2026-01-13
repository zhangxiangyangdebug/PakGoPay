package com.pakgopay.controller;

import com.pakgopay.common.reqeust.agent.AgentQueryRequest;
import com.pakgopay.common.response.CommonResponse;
import com.pakgopay.service.agent.AgentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/pakGoPay/server")
public class AgentController {
    @Autowired
    private AgentService agentService;

    @PostMapping("/queryAgent")
    public CommonResponse queryAgent(@RequestBody @Valid AgentQueryRequest agentQueryRequest, HttpServletRequest request) {
        log.info("queryAgent start");
//        try {
            return agentService.queryAgent(agentQueryRequest);
//        } catch (PakGoPayException e) {
//            log.error("queryAgent failed, code: {} message: {}", e.getErrorCode(), e.getMessage());
//            return CommonResponse.fail(e.getCode(), "queryAgent failed: " + e.getMessage());
//        }
    }
}
