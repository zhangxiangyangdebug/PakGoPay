package com.pakgopay.mapper;

import com.pakgopay.data.entity.report.AgentReportEntity;
import com.pakgopay.mapper.dto.AgentReportDto;
import org.apache.ibatis.annotations.Mapper;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface AgentReportMapper {

    /** Insert report */
    int insert(AgentReportDto dto);

    /** Update report by userId + recordDate + orderType + currency */
    int update(AgentReportDto dto);

    List<BigDecimal> commissionInfosByQuery(AgentReportEntity query);

    List<AgentReportDto> pageByQuery(AgentReportEntity query);
}
