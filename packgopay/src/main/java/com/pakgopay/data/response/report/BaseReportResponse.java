package com.pakgopay.data.response.report;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

@Data
public class BaseReportResponse {

    /**
     * card info (key: currency)
     */
    private Map<String, Map<String, BigDecimal>> cardInfo;

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
    private Integer totalNumber;
}
