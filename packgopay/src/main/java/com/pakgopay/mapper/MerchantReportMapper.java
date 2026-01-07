package com.pakgopay.mapper;

import com.pakgopay.entity.MerchantReportEntity;
import com.pakgopay.mapper.dto.MerchantReportDto;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface MerchantReportMapper {

    /** Insert report */
    int insert(MerchantReportDto dto);

    /** Update report (by userId + recordDate + orderType + currencyType) */
    int update(MerchantReportDto dto);

    /** Total count */
    long countByQuery(MerchantReportEntity query);

    /** Page query */
    List<MerchantReportDto> pageByQuery(MerchantReportEntity query);
}
