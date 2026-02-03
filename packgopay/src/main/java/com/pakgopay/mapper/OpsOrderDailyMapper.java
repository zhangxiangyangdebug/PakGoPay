package com.pakgopay.mapper;

import com.pakgopay.mapper.dto.OpsOrderDailyDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import com.pakgopay.data.entity.report.OpsReportQueryEntity;
import com.pakgopay.timer.data.ReportCurrencyRange;

@Mapper
public interface OpsOrderDailyMapper {

    int insert(OpsOrderDailyDto dto);

    int update(OpsOrderDailyDto dto);

    int batchUpsert(List<OpsOrderDailyDto> list);

    List<OpsOrderDailyDto> listLatest(OpsReportQueryEntity query);

    List<OpsOrderDailyDto> listOpsDailyStatsByDateRangeBatch(
            @Param("ranges") List<ReportCurrencyRange> ranges);
}
