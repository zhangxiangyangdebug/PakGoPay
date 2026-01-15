package com.pakgopay.data.response.merchant;

import com.pakgopay.mapper.dto.MerchantInfoDto;
import lombok.Data;

import java.util.List;

@Data
public class MerchantResponse {

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
