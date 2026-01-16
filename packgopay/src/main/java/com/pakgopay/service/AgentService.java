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
    CommonResponse queryAgent(@Valid AgentQueryRequest agentQueryRequest) throws PakGoPayException;

    void exportAgent(@Valid AgentQueryRequest agentQueryRequest, HttpServletResponse response) throws PakGoPayException, IOException;

    CommonResponse editAgent(@Valid AgentEditRequest agentEditRequest) throws PakGoPayException;

    CommonResponse addAgent(@Valid AgentAddRequest agentAddRequest) throws PakGoPayException;

    CommonResponse queryAgentAccount(@Valid AccountQueryRequest accountQueryRequest);

    void exportAgentAccount(@Valid AccountQueryRequest agentQueryRequest, HttpServletResponse response) throws IOException;

    CommonResponse editAgentAccount(@Valid AccountEditRequest accountEditRequest);

    CommonResponse addAgentAccount(@Valid AccountAddRequest accountAddRequest);
}
