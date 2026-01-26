package com.pakgopay.service;

import com.pakgopay.common.exception.PakGoPayException;
import com.pakgopay.data.reqeust.account.AccountAddRequest;
import com.pakgopay.data.reqeust.account.AccountEditRequest;
import com.pakgopay.data.reqeust.account.AccountQueryRequest;
import com.pakgopay.data.reqeust.agent.*;
import com.pakgopay.data.response.CommonResponse;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import java.io.IOException;

public interface AgentService {
    CommonResponse queryAgents(@Valid AgentQueryRequest agentQueryRequest) throws PakGoPayException;

    void exportAgents(@Valid AgentQueryRequest agentQueryRequest, HttpServletResponse response) throws PakGoPayException, IOException;

    CommonResponse updateAgent(@Valid AgentEditRequest agentEditRequest) throws PakGoPayException;

    CommonResponse createAgent(@Valid AgentAddRequest agentAddRequest) throws PakGoPayException;

    CommonResponse queryAgentAccounts(@Valid AccountQueryRequest accountQueryRequest);

    void exportAgentAccounts(@Valid AccountQueryRequest agentQueryRequest, HttpServletResponse response) throws IOException;

    CommonResponse updateAgentAccount(@Valid AccountEditRequest accountEditRequest);

    CommonResponse createAgentAccount(@Valid AccountAddRequest accountAddRequest);
}
