package com.pakgopay.data.response.currencyManagement;

import com.pakgopay.mapper.dto.CurrencyTypeDTO;
import lombok.Data;

import java.util.List;

@Data
public class CurrencyReponse {
    private List<CurrencyTypeDTO> currencyTypeDTOList;

    private Integer pageNo;

    private Integer pageSize;

    private Integer totalNumber;
}
