package com.pakgopay.data.response.account;

import com.pakgopay.mapper.dto.AccountStatementsDto;
import lombok.Data;

import java.util.List;

@Data
public class AccountStatementsResponse {


    private List<AccountStatementsDto> accountStatementsDtoList;

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
