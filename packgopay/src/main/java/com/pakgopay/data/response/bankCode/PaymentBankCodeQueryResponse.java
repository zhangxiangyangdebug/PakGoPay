package com.pakgopay.data.response.bankCode;

import com.pakgopay.mapper.dto.PaymentBankCodeDto;
import lombok.Data;

import java.util.List;

@Data
public class PaymentBankCodeQueryResponse {
    private Integer totalNumber;
    private Integer pageNo;
    private Integer pageSize;
    private List<PaymentBankCodeDto> paymentBankCodeDtoList;
}

