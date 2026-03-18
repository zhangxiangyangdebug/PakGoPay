package com.pakgopay.data.response.bankCode;

import com.pakgopay.mapper.dto.BankCodeDictDto;
import lombok.Data;

import java.util.List;

@Data
public class BankCodeQueryResponse {
    private Integer totalNumber;
    private Integer pageNo;
    private Integer pageSize;
    private List<BankCodeDictDto> bankCodeDictDtoList;
}

