package com.pakgopay.service.agent;

import com.pakgopay.common.exception.PakGoPayException;
import com.pakgopay.data.reqeust.agent.*;
import com.pakgopay.data.response.CommonResponse;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import java.io.IOException;

public interface AgentService {
    CommonResponse queryAgent(@Valid AgentQueryRequest agentQueryRequest) throws PakGoPayException;

    void exportAgent(@Valid AgentQueryRequest agentQueryRequest, HttpServletResponse response) throws PakGoPayException, IOException;

    CommonResponse editAgent(@Valid AgentEditRequest agentEditRequest) throws PakGoPayException;

    CommonResponse addAgent(@Valid AgentAddRequest agentAddRequest) throws PakGoPayException;

    CommonResponse queryAgentAccount(@Valid AgentAccountQueryRequest agentAccountQueryRequest);

    void exportAgentAccount(@Valid AgentAccountQueryRequest agentQueryRequest, HttpServletResponse response) throws IOException;

    CommonResponse editAgentAccount(@Valid AgentAccountEditRequest agentAccountEditRequest);

    CommonResponse addAgentAccount(@Valid AgentAccountAddRequest agentAccountAddRequest);
}
