package com.pakgopay.mapper;

import com.pakgopay.data.entity.report.BaseReportEntity;
import com.pakgopay.mapper.dto.CurrencyReportDto;
import com.pakgopay.mapper.dto.ReportCardSummaryDto;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface CurrencyReportMapper {

    /** Insert report */
    int insert(CurrencyReportDto dto);

    /** Update report by currency + recordDate + orderType */
    int update(CurrencyReportDto dto);

    /** Batch upsert reports */
    int batchUpsert(List<CurrencyReportDto> list);

    /** card summary: total rows + total amount + success amount */
    ReportCardSummaryDto cardSummaryByQuery(BaseReportEntity query);

    /** Page query */
    List<CurrencyReportDto> pageByQuery(BaseReportEntity query);
}
