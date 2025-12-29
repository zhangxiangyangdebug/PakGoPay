package com.pakgopay.mapper;


import com.pakgopay.mapper.dto.PaymentsDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Set;

@Mapper
public interface PaymentsMapper {

    List<PaymentsDto> findEByPaymentNo(@Param("paymentNo") Integer paymentNo);

    PaymentsDto findByPaymentId(@Param("paymentId") Long paymentId);

    List<PaymentsDto> getAllPayments();

    /**
     * Please modify the payment_ids field in the channels table simultaneously when inserting data.
     * @param dto data
     * @return modify lines
     */
    int insert(PaymentsDto dto);

    int updateByPaymentId(PaymentsDto dto);

    int deleteByPaymentId(@Param("paymentId") Long paymentId);

    List<PaymentsDto> findEnableInfoByPaymentNos(Integer paymentNo, Set<String> paymentIdList);
}
