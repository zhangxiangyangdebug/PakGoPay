package com.pakgopay.service.agent;

import com.pakgopay.common.reqeust.agent.AgentQueryRequest;
import com.pakgopay.common.response.CommonResponse;
import jakarta.validation.Valid;

public interface AgentService {
    CommonResponse queryAgent(@Valid AgentQueryRequest agentQueryRequest);
}
