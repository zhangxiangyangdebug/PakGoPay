package com.pakgopay.mapper;

import com.pakgopay.mapper.dto.BalanceDto;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface BalanceMapper {
    /** Query balance by userId */
    List<BalanceDto> findByUserId(@Param("userId") String userId);

    /**
     * Query balance list.
     * If userId is null or empty, return all.
     */
    List<BalanceDto> listByUserIds(@Param("userIds") List<String> userIds);

    /** Query balance by userId and currency */
    BalanceDto findByUserIdAndCurrency(@Param("userId") String userId, @Param("currency") String currency);

    /** Insert balance */
    int insert(BalanceDto dto);

    /** Update balance by userId (update non-null fields only) */
    int updateByUserId(BalanceDto dto);

    /** Increase/decrease available balance (delta can be positive/negative) */
    int addAvailableBalance(@Param("userId") String userId,
                            @Param("delta") BigDecimal delta,
                            @Param("currency") String currency,
                            @Param("updateTime") Long updateTime);

    /** Increase/decrease frozen balance (delta can be positive/negative) */
    int addFrozenBalance(@Param("userId") String userId,
                         @Param("delta") BigDecimal delta,
                         @Param("currency") String currency,
                         @Param("updateTime") Long updateTime);

    /** Release frozen balance back to available (total unchanged) */
    int releaseFrozenBalance(@Param("userId") String userId,
                             @Param("amount") BigDecimal amount,
                             @Param("currency") String currency,
                             @Param("updateTime") Long updateTime);

    int adjustBalance(@Param("userId") String userId,
                      @Param("amount") BigDecimal amount,
                      @Param("currency") String currency,
                      @Param("now") long now);

    int freezeForWithdraw(@Param("userId") String userId,
                          @Param("amount") BigDecimal amount,
                          @Param("currency") String currency,
                          @Param("now") long now);

    /** Callback success: confirm deduct */
    int confirmWithdraw(@Param("userId") String userId,
                        @Param("amount") BigDecimal amount,
                        @Param("currency") String currency,
                        @Param("now") long now);

    /** Callback failed: unfreeze */
    int cancelWithdraw(@Param("userId") String userId,
                       @Param("amount") BigDecimal amount,
                       @Param("currency") String currency,
                       @Param("now") long now);

    /** Payout success: release frozen and deduct total in one update */
    int comfirmPayoutBalance(@Param("userId") String userId,
                             @Param("amount") BigDecimal amount,
                             @Param("currency") String currency,
                             @Param("now") long now);
}
