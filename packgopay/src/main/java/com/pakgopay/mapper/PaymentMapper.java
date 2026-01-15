package com.pakgopay.mapper;


import com.pakgopay.data.entity.channel.PaymentEntity;
import com.pakgopay.mapper.dto.PaymentDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Set;

@Mapper
public interface PaymentMapper {

    List<PaymentDto> findEByPaymentNo(@Param("paymentNo") Integer paymentNo);

    PaymentDto findByPaymentId(@Param("paymentId") Long paymentId);

    List<PaymentDto> findByPaymentIds(@Param("paymentIdList") List<Long> paymentIdList);

    List<PaymentDto> getAllPayments();

    /**
     * Please modify the payment_ids field in the channels table simultaneously when inserting data.
     *
     * @param dto data
     * @return modify lines
     */
    int insert(PaymentDto dto);

    int updateByPaymentId(PaymentDto dto);

    int deleteByPaymentId(@Param("paymentId") Long paymentId);

    List<PaymentDto> findEnableInfoByPaymentNos(
            @Param("supportType") Integer supportType,
            @Param("paymentNo") Integer paymentNo, @Param("paymentIdList") Set<Long> paymentIdList);

    /** Count by query */
    Integer countByQuery(PaymentEntity entity);

    /** Page query */
    List<PaymentDto> pageByQuery(PaymentEntity entity);
}
