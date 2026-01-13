package com.pakgopay.service.agent;

import com.pakgopay.common.reqeust.agent.AgentQueryRequest;
import com.pakgopay.common.response.CommonResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class AgentServiceImpl implements AgentService{
    @Override
    public CommonResponse queryAgent(AgentQueryRequest agentQueryRequest) {
        return null;
    }
}
