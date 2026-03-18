package com.pakgopay.mapper;

import com.pakgopay.mapper.dto.PaymentChannelBankDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PaymentChannelBankMapper {

    /**
     * Query all relations by paymentId + currency.
     */
    List<PaymentChannelBankDto> listByPaymentCurrency(@Param("paymentId") Long paymentId,
                                                       @Param("currency") String currency);

    /**
     * Batch delete relations by paymentId + currency + (bankCode,supportType) keys.
     */
    int batchDeleteByKeys(@Param("paymentId") Long paymentId,
                                            @Param("currency") String currency,
                                            @Param("items") List<PaymentChannelBankDto> items);

    /**
     * Batch update status by paymentId + currency + (bankCode,supportType) keys.
     * Status is taken from each item.
     */
    int batchUpdateStatusByKeys(@Param("paymentId") Long paymentId,
                                                     @Param("currency") String currency,
                                                     @Param("updateTime") Long updateTime,
                                                     @Param("items") List<PaymentChannelBankDto> items);

    /**
     * Batch insert relation rows.
     */
    int batchInsert(@Param("list") List<PaymentChannelBankDto> list);
}
