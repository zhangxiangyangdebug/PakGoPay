package com.pakgopay.service.order.impl;

import com.pakgopay.common.constant.CommonConstant;
import com.pakgopay.common.enums.ResultCode;
import com.pakgopay.common.exception.PakGoPayException;
import com.pakgopay.common.reqeust.transaction.CollectionOrderRequest;
import com.pakgopay.common.response.CommonResponse;
import com.pakgopay.mapper.dto.MerchantInfoDto;
import com.pakgopay.service.order.ChannelPaymentService;
import com.pakgopay.service.order.CollectionOrderService;
import com.pakgopay.service.order.MerchantCheckService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class CollectionOrderServiceImpl implements CollectionOrderService {

    @Autowired
    MerchantCheckService merchantCheckService;

    @Autowired
    ChannelPaymentService channelPaymentService;

    @Override
    public CommonResponse createCollectionOrder(CollectionOrderRequest collectionOrderRequest) throws PakGoPayException {
        Long userId = Long.valueOf(collectionOrderRequest.getUserId());
        MerchantInfoDto merchantInfoDto = merchantCheckService.getConfigurationInfo(userId);
        // merchant is not exists
        if (merchantInfoDto == null) {
            throw new PakGoPayException(ResultCode.USER_IS_NOT_EXIST);
        }

        // request validate
        validateCollectionRequest(collectionOrderRequest, merchantInfoDto);
        // get payment id
        Long paymentId = channelPaymentService.getPaymentId(collectionOrderRequest, merchantInfoDto);

        return null;
    }

    private void validateCollectionRequest(CollectionOrderRequest collectionOrderRequest, MerchantInfoDto merchantInfoDto) throws PakGoPayException {
        Long userId = Long.valueOf(collectionOrderRequest.getUserId());
        // check ip white list
        if (merchantCheckService.isColIpAllowed(userId, collectionOrderRequest.getClientIp(), merchantInfoDto.getColWhiteIps())) {
            throw new PakGoPayException(ResultCode.IS_NOT_WHITE_IP);
        }

        // check Merchant order code is uniqueness
        if(merchantCheckService.existsColMerchantOrderNo(collectionOrderRequest.getMerchantOrderNo())){
            throw new PakGoPayException(ResultCode.MERCHANT_CODE_IS_EXISTS);
        }

        // amount check
        if (collectionOrderRequest.getAmount().compareTo(BigDecimal.ZERO) <= CommonConstant.ZERO) {
            throw new PakGoPayException(ResultCode.ORDER_PARAM_VALID, "The transaction amount must be greater than 0.");
        }

        // check user is enabled
        if (merchantCheckService.isEnableMerchant(merchantInfoDto.getStatus(), merchantInfoDto.getParentId())) {
            throw new PakGoPayException(ResultCode.USER_NOT_ENABLE);
        }
    }

}
