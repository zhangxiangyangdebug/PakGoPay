package com.pakgopay.mapper;

import com.pakgopay.mapper.dto.AccountStatementTaskCursorDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AccountStatementTaskCursorMapper {

    int upsertCursor(AccountStatementTaskCursorDto dto);

    List<AccountStatementTaskCursorDto> listCursorsByTaskType(@Param("taskType") String taskType,
                                                              @Param("limitSize") int limitSize);
}
