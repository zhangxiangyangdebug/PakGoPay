package com.pakgopay.data.response;

import com.pakgopay.mapper.dto.CollectionOrderDto;
import lombok.Data;

import java.util.List;

@Data
public class CollectionOrderPageResponse {

    private List<CollectionOrderDto> collectionOrderDtoList;
    private Integer pageNo;
    private Integer pageSize;
    private Integer totalNumber;
}
