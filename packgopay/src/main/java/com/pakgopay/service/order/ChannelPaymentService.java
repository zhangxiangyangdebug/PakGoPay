package com.pakgopay.service.order;


import com.pakgopay.common.constant.CommonConstant;
import com.pakgopay.common.enums.ResultCode;
import com.pakgopay.common.exception.PakGoPayException;
import com.pakgopay.common.reqeust.transaction.CollectionOrderRequest;
import com.pakgopay.mapper.ChannelsMapper;
import com.pakgopay.mapper.PaymentsMapper;
import com.pakgopay.mapper.dto.MerchantInfoDto;
import com.pakgopay.mapper.dto.PaymentsDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ChannelPaymentService {

    @Autowired
    private PaymentsMapper paymentsMapper;

    @Autowired
    private ChannelsMapper channelsMapper;

    public void getPaymentId(CollectionOrderRequest collectionOrderRequest, MerchantInfoDto merchantInfoDto) throws PakGoPayException {
        Long userId = Long.valueOf(collectionOrderRequest.getUserId());

        String channelIds = merchantInfoDto.getChannelIds();
        if(!StringUtils.hasText(channelIds)){
            throw new PakGoPayException(ResultCode.ORDER_PARAM_VALID, "merchant has not channel");
        }


        Set<String> channelIdList = Arrays.stream(channelIds.split(",")).map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
        if (channelIdList.isEmpty()) {
            throw new PakGoPayException(ResultCode.ORDER_PARAM_VALID, "merchant has not channel");
        }


        String paymentIds = channelsMapper.getPaymentIdsByChannelIds(channelIdList.stream().toList(), CommonConstant.ENABLE_STATUS_ENABLE);
        if(!StringUtils.hasText(paymentIds)){
            throw new PakGoPayException(ResultCode.ORDER_PARAM_VALID, "merchant has not payment");
        }
        Set<String> paymentIdList = Arrays.stream(channelIds.split(",")).map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
        if (paymentIdList.isEmpty()) {
            throw new PakGoPayException(ResultCode.ORDER_PARAM_VALID, "merchant has not payment");
        }


        List<PaymentsDto> paymentsDtoList = paymentsMapper.findEnableInfoByPaymentNo(collectionOrderRequest.getPaymentNo(), paymentIdList);
        if (paymentsDtoList == null || paymentsDtoList.isEmpty()) {
            throw new PakGoPayException(ResultCode.ORDER_PARAM_VALID, "merchants do not have matching payment");
        }

        //  投票通道成功率高的


    }
}
