package com.pakgopay.data.response.channel;

import com.pakgopay.mapper.dto.PaymentDto;
import lombok.Data;

import java.util.List;

@Data
public class PaymentResponse {

    private List<PaymentDto> paymentDtoList;

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
