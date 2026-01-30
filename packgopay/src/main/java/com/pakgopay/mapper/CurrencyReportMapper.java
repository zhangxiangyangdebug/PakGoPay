package com.pakgopay.mapper;

import com.pakgopay.data.entity.report.BaseReportEntity;
import com.pakgopay.mapper.dto.CurrencyReportDto;
import org.apache.ibatis.annotations.Mapper;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface CurrencyReportMapper {

    /** Insert report */
    int insert(CurrencyReportDto dto);

    /** Update report by currency + recordDate + orderType */
    int update(CurrencyReportDto dto);

    /** Batch upsert reports */
    int batchUpsert(List<CurrencyReportDto> list);

    /** order_balance list by query */
    List<BigDecimal> balanceInfosByQuery(BaseReportEntity query);

    /** Page query */
    List<CurrencyReportDto> pageByQuery(BaseReportEntity query);
}
