package com.pakgopay.mapper;

import com.pakgopay.data.entity.report.ChannelReportEntity;
import com.pakgopay.mapper.dto.ChannelReportDto;
import org.apache.ibatis.annotations.Mapper;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface ChannelReportMapper {

    /** Insert report */
    int insert(ChannelReportDto dto);

    /** Update report (by userId + recordDate + orderType + currency) */
    int update(ChannelReportDto dto);

    /** balance infos */
    List<BigDecimal> balanceInfosByQuery(ChannelReportEntity query);

    /** Page query */
    List<ChannelReportDto> pageByQuery(ChannelReportEntity query);
}
