package com.pakgopay.mapper;

import com.pakgopay.data.entity.OrderQueryEntity;
import com.pakgopay.mapper.dto.ChannelReportDto;
import com.pakgopay.mapper.dto.CurrencyReportDto;
import com.pakgopay.mapper.dto.MerchantReportDto;
import com.pakgopay.mapper.dto.PaymentReportDto;
import com.pakgopay.mapper.dto.PayOrderDto;
import com.pakgopay.timer.data.ReportCurrencyRange;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface PayOrderMapper {

    Integer isExitMerchantOrderNo(@Param(value = "merchantOrderNo") String merchantOrderNo);

    /** Query order by order ID */
    Optional<PayOrderDto> findByTransactionNo(@Param("transactionNo") String transactionNo);

    /** Insert order */
    int insert(PayOrderDto dto);

    /** Update order by order ID (update non-null fields) */
    int updateByTransactionNo(@Param("dto") PayOrderDto dto);

    /** Update order by transactionNo when status is processing */
    int updateByTransactionNoWhenProcessing(@Param("dto") PayOrderDto dto,
                                            @Param("currentStatus") String currentStatus);

    /** Increase callback retry times (atomic) */
    int increaseCallbackTimes(@Param("transactionNo") String transactionNo,
                              @Param("lastCallbackTime") Long lastCallbackTime,
                              @Param("increment") Integer increment,
                              @Param("successCallbackTime") Long successCallbackTime);

    /** Query by payment IDs and time range */
    List<PayOrderDto> getPayOrderInfosByPaymentIds(@Param("paymentIds") List<Long> paymentIds,
                                                   @Param("startTime") Long startTime,
                                                   @Param("endTime") Long endTime);

    List<MerchantReportDto> listMerchantReportStatsBatch(@Param("ranges") List<ReportCurrencyRange> ranges,
                                                         @Param("successStatus") String successStatus);

    List<ChannelReportDto> listChannelReportStatsBatch(@Param("ranges") List<ReportCurrencyRange> ranges,
                                                       @Param("successStatus") String successStatus);

    List<PaymentReportDto> listPaymentReportStatsBatch(@Param("ranges") List<ReportCurrencyRange> ranges,
                                                       @Param("successStatus") String successStatus);

    List<CurrencyReportDto> listCurrencyReportStatsBatch(@Param("ranges") List<ReportCurrencyRange> ranges,
                                                         @Param("successStatus") String successStatus);

    MerchantReportDto sumMerchantStatsByUserId(@Param("merchantUserId") String merchantUserId,
                                               @Param("currency") String currency,
                                               @Param("startTime") Long startTime,
                                               @Param("endTime") Long endTime,
                                               @Param("successStatus") String successStatus);

    /** Count by query */
    Integer countByQuery(@Param("q") OrderQueryEntity query);

    /** Page query */
    List<PayOrderDto> pageByQuery(@Param("q") OrderQueryEntity query);
}
