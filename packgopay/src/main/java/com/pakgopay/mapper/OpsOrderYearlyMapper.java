package com.pakgopay.mapper;

import com.pakgopay.mapper.dto.OpsOrderYearlyDto;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import com.pakgopay.data.entity.report.OpsReportQueryEntity;

@Mapper
public interface OpsOrderYearlyMapper {

    int insert(OpsOrderYearlyDto dto);

    int update(OpsOrderYearlyDto dto);

    int batchUpsert(List<OpsOrderYearlyDto> list);

    List<OpsOrderYearlyDto> listLatest(OpsReportQueryEntity query);
}
