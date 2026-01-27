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
    CommonResponse queryAgents(AgentQueryRequest agentQueryRequest) throws PakGoPayException;

    void exportAgents(AgentQueryRequest agentQueryRequest, HttpServletResponse response) throws PakGoPayException, IOException;

    CommonResponse updateAgent(AgentEditRequest agentEditRequest) throws PakGoPayException;

    CommonResponse createAgent(AgentAddRequest agentAddRequest) throws PakGoPayException;

    CommonResponse queryAgentAccounts(AccountQueryRequest accountQueryRequest);

    void exportAgentAccounts(AccountQueryRequest agentQueryRequest, HttpServletResponse response) throws IOException;

    CommonResponse updateAgentAccount(AccountEditRequest accountEditRequest);

    CommonResponse createAgentAccount(AccountAddRequest accountAddRequest);
}
