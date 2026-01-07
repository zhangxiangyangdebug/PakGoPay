package com.pakgopay.common.response.report;

import com.pakgopay.mapper.dto.MerchantReportDto;
import lombok.Data;

import java.util.List;

@Data
public class MerchantReportResponse {
    /**
     * merchant report info list
     */
    private List<MerchantReportDto> merchantReportDtoList;

    /**
     * page no
     */
    private Integer pageNo;

    /**
     * page size
     */
    private Integer pageSize;


    /**
     * total number
     */
    private Long totalNumber;
}
