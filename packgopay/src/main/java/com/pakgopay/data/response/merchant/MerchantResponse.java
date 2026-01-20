package com.pakgopay.data.response.merchant;

import com.pakgopay.mapper.dto.MerchantInfoDto;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
public class MerchantResponse {

    /**
     * card info (key: currency)
     */
    private Map<String, Map<String, BigDecimal>> cardInfo;

    private List<MerchantInfoDto> merchantInfoDtoList;

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
