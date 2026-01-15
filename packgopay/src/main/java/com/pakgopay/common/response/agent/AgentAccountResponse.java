package com.pakgopay.common.response.agent;

import com.pakgopay.mapper.dto.WithdrawalAccountsDto;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
public class AgentAccountResponse {

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
