package com.pakgopay.data.response.report;

import com.pakgopay.mapper.dto.AgentReportDto;
import lombok.Data;

import java.util.List;

@Data
public class AgentReportResponse extends BaseReportResponse {

    /**
     * agent report info list
     */
    private List<AgentReportDto> agentReportDtoList;
}
