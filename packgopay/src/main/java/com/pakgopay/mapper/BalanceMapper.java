package com.pakgopay.mapper;

import com.pakgopay.mapper.dto.BalanceDto;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

@Mapper
public interface BalanceMapper {
    /** Query balance by userId */
    Optional<BalanceDto> findByUserId(@Param("userId") String userId);

    /** Insert balance */
    int insert(BalanceDto dto);

    /** Update balance by userId (update non-null fields only) */
    int updateByUserId(BalanceDto dto);

    /** Increase/decrease available balance (delta can be positive/negative) */
    int addAvailableBalance(@Param("userId") String userId,
                            @Param("delta") java.math.BigDecimal delta,
                            @Param("updateTime") LocalDateTime updateTime);

    /** Increase/decrease frozen balance (delta can be positive/negative) */
    int addFrozenBalance(@Param("userId") String userId,
                         @Param("delta") java.math.BigDecimal delta,
                         @Param("updateTime") LocalDateTime updateTime);
}
