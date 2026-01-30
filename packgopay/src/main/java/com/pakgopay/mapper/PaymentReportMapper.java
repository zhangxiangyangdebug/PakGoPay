package com.pakgopay.mapper;

import com.pakgopay.data.entity.report.PaymentReportEntity;
import com.pakgopay.mapper.dto.PaymentReportDto;
import org.apache.ibatis.annotations.Mapper;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface PaymentReportMapper {

    /** Insert report */
    int insert(PaymentReportDto dto);

    /** Update report by paymentId + recordDate + orderType + currency */
    int update(PaymentReportDto dto);

    /** Batch upsert reports */
    int batchUpsert(List<PaymentReportDto> list);

    /** balance infos */
    List<BigDecimal> balanceInfosByQuery(PaymentReportEntity query);

    /** Page query */
    List<PaymentReportDto> pageByQuery(PaymentReportEntity query);
}
