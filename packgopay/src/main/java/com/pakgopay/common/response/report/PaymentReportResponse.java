package com.pakgopay.common.response.report;

import com.pakgopay.mapper.dto.PaymentReportDto;
import lombok.Data;

import java.util.List;

@Data
public class PaymentReportResponse extends BaseReportResponse {

    /**
     * payment report info list
     */
    private List<PaymentReportDto> paymentReportDtoList;
}
