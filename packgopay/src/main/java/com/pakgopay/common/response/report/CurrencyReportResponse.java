package com.pakgopay.common.response.report;

import com.pakgopay.mapper.dto.CurrencyReportDto;
import lombok.Data;

import java.util.List;

@Data
public class CurrencyReportResponse extends BaseReportResponse {

    /**
     * currency report info list
     */
    private List<CurrencyReportDto> currencyReportDtoList;
}
