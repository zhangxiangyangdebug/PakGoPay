package com.pakgopay.data.response;

import com.pakgopay.mapper.dto.OrderFlowLogDto;
import lombok.Data;

import java.util.List;

@Data
public class OrderFlowLogQueryResponse {

    private String transactionNo;
    private List<OrderFlowLogDto> flowLogs;
}

