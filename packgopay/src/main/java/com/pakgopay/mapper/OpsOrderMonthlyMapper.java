package com.pakgopay.mapper;

import com.pakgopay.mapper.dto.OpsOrderMonthlyDto;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import com.pakgopay.data.entity.report.OpsReportQueryEntity;

@Mapper
public interface OpsOrderMonthlyMapper {

    int insert(OpsOrderMonthlyDto dto);

    int update(OpsOrderMonthlyDto dto);

    int batchUpsert(List<OpsOrderMonthlyDto> list);

    List<OpsOrderMonthlyDto> listLatest(OpsReportQueryEntity query);
}
