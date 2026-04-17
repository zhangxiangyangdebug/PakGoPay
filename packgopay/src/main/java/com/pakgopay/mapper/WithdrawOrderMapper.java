package com.pakgopay.mapper;

import com.pakgopay.data.entity.account.WithdrawOrderEntity;
import com.pakgopay.mapper.dto.WithdrawOrderDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface WithdrawOrderMapper {

    int insert(WithdrawOrderEntity entity);

    int updateByWithdrawNo(WithdrawOrderEntity entity);

    int updateStatusByWithdrawNo(@Param("withdrawNo") String withdrawNo,
                                 @Param("status") Integer status,
                                 @Param("failReason") String failReason,
                                 @Param("remark") String remark,
                                 @Param("updateBy") String updateBy,
                                 @Param("updateTime") Long updateTime);

    Optional<WithdrawOrderDto> findByWithdrawNo(@Param("withdrawNo") String withdrawNo);

    Optional<WithdrawOrderDto> findById(@Param("id") Long id);

    Integer countByQuery(WithdrawOrderEntity entity);

    List<WithdrawOrderDto> pageByQuery(WithdrawOrderEntity entity);
}
