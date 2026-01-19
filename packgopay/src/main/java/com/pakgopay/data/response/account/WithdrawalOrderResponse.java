package com.pakgopay.data.response.account;

import com.pakgopay.mapper.dto.WithdrawalOrderDto;
import lombok.Data;

import java.util.List;

@Data
public class WithdrawalOrderResponse {


    private List<WithdrawalOrderDto> withdrawalOrderDtoList;

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
