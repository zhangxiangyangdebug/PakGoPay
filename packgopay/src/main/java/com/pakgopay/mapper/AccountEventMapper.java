package com.pakgopay.mapper;

import com.pakgopay.mapper.dto.AccountEventDto;
import com.pakgopay.mapper.dto.AccountEventQueryDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AccountEventMapper {

    /**
     * Idempotent batch append.
     * Return inserted row count (duplicates are skipped).
     */
    int insertEventBatch(@Param("tableName") String tableName,
                         @Param("list") List<AccountEventDto> list);

    /**
     * Claim pending rows to PROCESSING with SKIP LOCKED semantics.
     */
    List<AccountEventDto> claimPendingEventsFromTable(@Param("tableName") String tableName,
                                                      @Param("eventTypes") List<String> eventTypes,
                                                      @Param("limitSize") Integer limitSize,
                                                      @Param("now") Long now);

    List<AccountEventQueryDto> listByTransactionNo(@Param("transactionNo") String transactionNo,
                                                   @Param("partitionStart") Long partitionStart,
                                                   @Param("partitionEnd") Long partitionEnd);

    /**
     * Mark claimed rows done and stamp batchNo.
     */
    int markDoneByIds(@Param("ids") List<Long> ids,
                      @Param("batchNo") String batchNo,
                      @Param("now") Long now,
                      @Param("partitionStart") Long partitionStart,
                      @Param("partitionEnd") Long partitionEnd);

    /**
     * Mark claimed rows failed and increase retry_count.
     */
    int markFailedByIds(@Param("ids") List<Long> ids,
                        @Param("error") String error,
                        @Param("now") Long now,
                        @Param("partitionStart") Long partitionStart,
                        @Param("partitionEnd") Long partitionEnd);
}
