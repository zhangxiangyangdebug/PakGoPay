package com.pakgopay.service.agent;

import com.pakgopay.common.exception.PakGoPayException;
import com.pakgopay.common.reqeust.agent.AgentEditRequest;
import com.pakgopay.common.reqeust.agent.AgentQueryRequest;
import com.pakgopay.common.response.CommonResponse;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import java.io.IOException;

public interface AgentService {
    CommonResponse queryAgent(@Valid AgentQueryRequest agentQueryRequest) throws PakGoPayException;

    void exportAgent(@Valid AgentQueryRequest agentQueryRequest, HttpServletResponse response) throws PakGoPayException, IOException;

    CommonResponse editAgent(@Valid AgentEditRequest agentEditRequest) throws PakGoPayException;
}
