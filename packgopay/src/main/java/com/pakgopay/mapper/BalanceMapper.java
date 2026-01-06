package com.pakgopay.mapper;

import com.pakgopay.mapper.dto.BalanceDto;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.data.repository.query.Param;

import java.util.List;

@Mapper
public interface BalanceMapper {
    /** Query balance by userId */
    List<BalanceDto> findByUserId(@Param("userId") String userId);

    /** Query balance by userId and currency */
    BalanceDto findByUserIdAndCurrency(@Param("userId") String userId, @Param("currency") String currency);

    /** Insert balance */
    int insert(BalanceDto dto);

    /** Update balance by userId (update non-null fields only) */
    int updateByUserId(BalanceDto dto);

    /** Increase/decrease available balance (delta can be positive/negative) */
    int addAvailableBalance(@Param("userId") String userId,
                            @Param("delta") java.math.BigDecimal delta,
                            @Param("updateTime") Long updateTime);

    /** Increase/decrease frozen balance (delta can be positive/negative) */
    int addFrozenBalance(@Param("userId") String userId,
                         @Param("delta") java.math.BigDecimal delta,
                         @Param("updateTime") Long updateTime);
}
