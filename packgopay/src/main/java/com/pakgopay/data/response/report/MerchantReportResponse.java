package com.pakgopay.data.response.report;

import com.pakgopay.mapper.dto.MerchantReportDto;
import lombok.Data;

import java.util.List;

@Data
public class MerchantReportResponse extends BaseReportResponse {
    /**
     * merchant report info list
     */
    private List<MerchantReportDto> merchantReportDtoList;
}
