package com.pakgopay.common.response.agent;

import com.pakgopay.mapper.dto.AgentInfoDto;
import lombok.Data;

import java.util.List;

@Data
public class AgentResponse {

    private List<AgentInfoDto> agentInfoDtoList;

    /**
     * page no
     */
    private Integer pageNo;

    /**
     * page size
     */
    private Integer pageSize;


    /**
     * total number
     */
    private Integer totalNumber;
}
