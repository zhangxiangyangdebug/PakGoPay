package com.pakgopay.mapper;

import com.pakgopay.data.entity.report.ChannelReportEntity;
import com.pakgopay.mapper.dto.ChannelReportDto;
import com.pakgopay.mapper.dto.ReportCardSummaryDto;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ChannelReportMapper {

    /** Insert report */
    int insert(ChannelReportDto dto);

    /** Update report (by userId + recordDate + orderType + currency) */
    int update(ChannelReportDto dto);

    /** Batch upsert reports */
    int batchUpsert(List<ChannelReportDto> list);

    /** card summary: total rows + total amount + success amount */
    ReportCardSummaryDto cardSummaryByQuery(ChannelReportEntity query);

    /** Page query */
    List<ChannelReportDto> pageByQuery(ChannelReportEntity query);
}
