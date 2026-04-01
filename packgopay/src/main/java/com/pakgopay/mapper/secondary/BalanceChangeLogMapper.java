package com.pakgopay.mapper.secondary;

import com.pakgopay.mapper.dto.BalanceChangeLogDto;

public interface BalanceChangeLogMapper {

    int insertIgnore(BalanceChangeLogDto dto);
}
