package com.pakgopay.mapper;

import com.pakgopay.data.reqeust.bankCode.BankCodeQueryRequest;
import com.pakgopay.data.reqeust.bankCode.PaymentBankCodeQueryRequest;
import com.pakgopay.mapper.dto.BankCodeDictDto;
import com.pakgopay.mapper.dto.PaymentBankCodeDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface BankCodeDictMapper {

    int insert(BankCodeDictDto dto);

    int updateById(BankCodeDictDto dto);

    int deleteById(@Param("id") Long id);

    BankCodeDictDto findById(@Param("id") Long id);

    List<BankCodeDictDto> listByCurrencyCode(@Param("currencyCode") String currencyCode);

    List<BankCodeDictDto> listByCurrencyCountry(@Param("currencyCode") String currencyCode,
                                                @Param("country") String country);

    List<BankCodeDictDto> listByQuery(BankCodeQueryRequest request);

    Integer countByQuery(BankCodeQueryRequest request);

    List<BankCodeDictDto> pageByQuery(BankCodeQueryRequest request);

    Integer countByPaymentCurrency(PaymentBankCodeQueryRequest request);

    List<PaymentBankCodeDto> pageByPaymentCurrency(PaymentBankCodeQueryRequest request);
}
