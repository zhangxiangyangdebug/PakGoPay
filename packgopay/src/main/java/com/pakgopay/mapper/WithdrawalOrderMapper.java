package com.pakgopay.mapper;

import com.pakgopay.data.entity.agent.AccountInfoEntity;
import com.pakgopay.mapper.dto.WithdrawalOrderDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface WithdrawalOrderMapper {

    int insert(WithdrawalOrderDto dto);

    int updateById(WithdrawalOrderDto dto);

    int deleteById(@Param("id") Long id);

    WithdrawalOrderDto selectById(@Param("id") Long id);

    List<WithdrawalOrderDto> selectList(@Param("q") WithdrawalOrderDto query);

    int count(@Param("q") WithdrawalOrderDto query);

    Integer countByQuery(AccountInfoEntity entity);

    List<WithdrawalOrderDto> pageByQuery(AccountInfoEntity entity);
}
