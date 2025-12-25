package com.pakgopay.mapper;

import com.pakgopay.mapper.dto.CollectionOrderDto;

import java.util.Optional;

public interface CollectionOrderMapper {

    Optional<CollectionOrderDto> getRequestTimes(String merchantId);
}
