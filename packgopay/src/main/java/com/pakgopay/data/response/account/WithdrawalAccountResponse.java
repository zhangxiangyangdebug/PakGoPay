package com.pakgopay.data.response.account;

import com.pakgopay.mapper.dto.WithdrawalAccountsDto;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
public class WithdrawalAccountResponse {

    /**
     * card info (key: currency)
     */
    private Map<String, Map<String, BigDecimal>> cardInfo;

    private List<WithdrawalAccountsDto> withdrawalAccountsDtoList;

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
