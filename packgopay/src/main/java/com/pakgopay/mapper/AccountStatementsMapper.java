package com.pakgopay.mapper;

import com.pakgopay.data.entity.account.AccountStatementEntity;
import com.pakgopay.mapper.dto.AccountStatementsDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AccountStatementsMapper {

    int insert(AccountStatementsDto dto);

    int updateById(AccountStatementsDto dto);

    int deleteById(@Param("id") String id);

    AccountStatementsDto selectById(@Param("id") String id);

    List<AccountStatementsDto> selectList(@Param("q") AccountStatementsDto query);

    int count(@Param("q") AccountStatementsDto query);

    Integer countByQuery(AccountStatementEntity entity);

    List<AccountStatementsDto> pageByQuery(AccountStatementEntity entity);
}
