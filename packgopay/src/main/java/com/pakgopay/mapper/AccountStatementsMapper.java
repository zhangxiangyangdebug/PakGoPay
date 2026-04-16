package com.pakgopay.mapper;

import com.pakgopay.data.entity.account.AccountStatementEntity;
import com.pakgopay.mapper.dto.AccountStatementEnqueueDto;
import com.pakgopay.mapper.dto.AccountStatementsDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AccountStatementsMapper {

    int insert(AccountStatementsDto dto);

    List<AccountStatementEnqueueDto> batchInsertReturning(@Param("list") List<AccountStatementsDto> list);

    int updateBySerialNo(AccountStatementsDto dto);

    int batchUpdateBySerialNo(@Param("list") List<AccountStatementsDto> list);

    AccountStatementsDto selectBySerialNoFromTable(@Param("tableName") String tableName,
                                                   @Param("serialNo") String serialNo);

    Integer countByQuery(AccountStatementEntity entity);

    List<AccountStatementsDto> pageByQuery(AccountStatementEntity entity);

    AccountStatementsDto selectEarliestPendingApplyAnchor(@Param("userId") String userId,
                                                          @Param("currency") String currency);

    AccountStatementsDto selectEarliestPendingSnapshotAnchor(@Param("userId") String userId,
                                                             @Param("currency") String currency);

    List<AccountStatementsDto> listPendingBalanceApplyFromTable(@Param("tableName") String tableName,
                                                                @Param("userId") String userId,
                                                                @Param("currency") String currency,
                                                                @Param("startTime") Long startTime,
                                                                @Param("endTime") Long endTime,
                                                                @Param("limitSize") int limitSize);

    AccountStatementsDto selectLatestCompletedBeforeFromTable(@Param("tableName") String tableName,
                                                              @Param("userId") String userId,
                                                              @Param("currency") String currency,
                                                              @Param("startTime") Long startTime,
                                                              @Param("endTime") Long endTime,
                                                              @Param("id") Long id);

    List<AccountStatementsDto> listPendingBalanceSnapshotsFromTable(@Param("tableName") String tableName,
                                                                    @Param("userId") String userId,
                                                                    @Param("currency") String currency,
                                                                    @Param("startTime") Long startTime,
                                                                    @Param("endTime") Long endTime,
                                                                    @Param("limitSize") int limitSize);

    int batchUpdateStatusBySerialNo(@Param("list") List<AccountStatementsDto> list);
}
