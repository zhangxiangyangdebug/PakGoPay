package com.pakgopay.mapper;

import com.pakgopay.data.entity.report.MerchantReportEntity;
import com.pakgopay.mapper.dto.MerchantReportDto;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface MerchantReportMapper {

    /** Insert report */
    int insert(MerchantReportDto dto);

    /** Update report (by userId + recordDate + orderType + currency) */
    int update(MerchantReportDto dto);

    /** Batch upsert reports */
    int batchUpsert(List<MerchantReportDto> list);

    /** Total count */
    Integer countByQuery(MerchantReportEntity query);

    /** Page query */
    List<MerchantReportDto> pageByQuery(MerchantReportEntity query);
}
