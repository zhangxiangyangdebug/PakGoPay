package com.pakgopay.data.response;

import com.pakgopay.mapper.dto.PayOrderDto;
import lombok.Data;

import java.util.List;

@Data
public class PayOutOrderPageResponse {

    private List<PayOrderDto> payOrderDtoList;
    private Integer pageNo;
    private Integer pageSize;
    private Integer totalNumber;
}
