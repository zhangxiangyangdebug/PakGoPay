package com.pakgopay.mapper;

import com.pakgopay.data.entity.OrderQueryEntity;
import com.pakgopay.mapper.dto.CollectionOrderDto;
import com.pakgopay.mapper.dto.ChannelReportDto;
import com.pakgopay.mapper.dto.CurrencyReportDto;
import com.pakgopay.mapper.dto.PaymentReportDto;
import com.pakgopay.mapper.dto.MerchantReportDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface CollectionOrderMapper {

    Integer isExitMerchantOrderNo(@Param(value = "merchantOrderNo") String merchantOrderNo);

    /** Query order by order ID */
    Optional<CollectionOrderDto> findByTransactionNo(@Param("transactionNo") String transactionNo);

    /** Insert order */
    int insert(CollectionOrderDto dto);

    /** Update order by order ID */
    int updateByTransactionNo(CollectionOrderDto dto);

    /** Increase callback retry times */
    int increaseCallbackTimes(@Param("transactionNo") String transactionNo,
                              @Param("lastCallbackTime") Long lastCallbackTime);

    /** Query orders by payment IDs and time range */
    List<CollectionOrderDto> getCollectionOrderInfosByPaymentIds(
            @Param("paymentIds") List<Long> paymentIds,
            @Param("startTime") Long startTime,
            @Param("endTime") Long endTime
    );

    /** Aggregate merchant report data by date range */
    MerchantReportDto sumMerchantReport(
            @Param("userId") String userId,
            @Param("currency") String currency,
            @Param("startTime") Long startTime,
            @Param("endTime") Long endTime,
            @Param("successStatus") String successStatus);

    ChannelReportDto sumChannelReport(
            @Param("channelId") Long channelId,
            @Param("currency") String currency,
            @Param("startTime") Long startTime,
            @Param("endTime") Long endTime,
            @Param("successStatus") String successStatus);

    PaymentReportDto sumPaymentReport(
            @Param("paymentId") Long paymentId,
            @Param("currency") String currency,
            @Param("startTime") Long startTime,
            @Param("endTime") Long endTime,
            @Param("successStatus") String successStatus);

    CurrencyReportDto sumCurrencyReport(
            @Param("currency") String currency,
            @Param("startTime") Long startTime,
            @Param("endTime") Long endTime,
            @Param("successStatus") String successStatus);

    List<MerchantReportDto> listMerchantReportStats(@Param("currency") String currency,
                                                    @Param("startTime") Long startTime,
                                                    @Param("endTime") Long endTime,
                                                    @Param("successStatus") String successStatus);

    List<ChannelReportDto> listChannelReportStats(@Param("currency") String currency,
                                                  @Param("startTime") Long startTime,
                                                  @Param("endTime") Long endTime,
                                                  @Param("successStatus") String successStatus);

    List<PaymentReportDto> listPaymentReportStats(@Param("currency") String currency,
                                                  @Param("startTime") Long startTime,
                                                  @Param("endTime") Long endTime,
                                                  @Param("successStatus") String successStatus);

    List<CurrencyReportDto> listCurrencyReportStats(@Param("currency") String currency,
                                                    @Param("startTime") Long startTime,
                                                    @Param("endTime") Long endTime,
                                                    @Param("successStatus") String successStatus);

    /** Count by query */
    Integer countByQuery(@Param("q") OrderQueryEntity query);

    /** Page query */
    List<CollectionOrderDto> pageByQuery(@Param("q") OrderQueryEntity query);
}
