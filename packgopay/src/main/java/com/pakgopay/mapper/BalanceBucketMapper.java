package com.pakgopay.mapper;

import com.pakgopay.mapper.dto.BalanceDto;
import com.pakgopay.mapper.dto.BalanceBucketDeltaDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface BalanceBucketMapper {

    List<BalanceDto> findByUserId(@Param("userId") String userId);

    List<BalanceDto> listByUserIds(@Param("userIds") List<String> userIds);

    BalanceDto findByUserIdAndCurrency(@Param("userId") String userId,
                                       @Param("currency") String currency);

    int insertZeroBuckets(@Param("userId") String userId,
                          @Param("currency") String currency,
                          @Param("createTime") long createTime,
                          @Param("updateTime") long updateTime);

    List<BalanceDto> listBuckets(@Param("userId") String userId,
                                 @Param("currency") String currency);

    List<BalanceDto> listBucketsForUpdate(@Param("userId") String userId,
                                          @Param("currency") String currency);

    int batchApplyDeltas(@Param("userId") String userId,
                         @Param("currency") String currency,
                         @Param("updateTime") long updateTime,
                         @Param("list") List<BalanceBucketDeltaDto> list);

    int freezeBalance(@Param("userId") String userId,
                      @Param("amount") BigDecimal amount,
                      @Param("currency") String currency,
                      @Param("bucketNo") Integer bucketNo,
                      @Param("updateTime") Long updateTime);

    int upsertCreditBalance(@Param("userId") String userId,
                            @Param("currency") String currency,
                            @Param("bucketNo") Integer bucketNo,
                            @Param("amount") BigDecimal amount,
                            @Param("now") long now);

    int releaseFrozenBalance(@Param("userId") String userId,
                             @Param("amount") BigDecimal amount,
                             @Param("currency") String currency,
                             @Param("bucketNo") Integer bucketNo,
                             @Param("updateTime") Long updateTime);

    int adjustBalance(@Param("userId") String userId,
                      @Param("amount") BigDecimal amount,
                      @Param("currency") String currency,
                      @Param("bucketNo") Integer bucketNo,
                      @Param("now") long now);

    int freezeForWithdraw(@Param("userId") String userId,
                          @Param("amount") BigDecimal amount,
                          @Param("currency") String currency,
                          @Param("bucketNo") Integer bucketNo,
                          @Param("now") long now);

    int confirmWithdraw(@Param("userId") String userId,
                        @Param("amount") BigDecimal amount,
                        @Param("currency") String currency,
                        @Param("bucketNo") Integer bucketNo,
                        @Param("now") long now);

    int cancelWithdraw(@Param("userId") String userId,
                       @Param("amount") BigDecimal amount,
                       @Param("currency") String currency,
                       @Param("bucketNo") Integer bucketNo,
                       @Param("now") long now);

    int confirmPayoutBalance(@Param("userId") String userId,
                             @Param("amount") BigDecimal amount,
                             @Param("currency") String currency,
                             @Param("bucketNo") Integer bucketNo,
                             @Param("now") long now);
}
