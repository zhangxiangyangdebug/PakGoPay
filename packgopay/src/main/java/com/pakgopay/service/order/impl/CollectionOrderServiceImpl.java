package com.pakgopay.service.order.impl;

import com.pakgopay.common.enums.ResultCode;
import com.pakgopay.common.exception.PakGoPayException;
import com.pakgopay.common.reqeust.transaction.CollectionOrderRequest;
import com.pakgopay.common.response.CommonResponse;
import com.pakgopay.mapper.dto.MerchantInfoDto;
import com.pakgopay.util.MerchantCheckUtil;
import com.pakgopay.mapper.CollectionOrderMapper;
import com.pakgopay.service.order.CollectionOrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class CollectionOrderServiceImpl implements CollectionOrderService {

    @Autowired
    MerchantCheckUtil merchantCheckUtil;

    @Autowired
    CollectionOrderMapper collectionOrderMapper;

    @Override
    public CommonResponse createCollectionOrder(CollectionOrderRequest collectionOrderRequest) throws PakGoPayException {
        // request validate
        validateCollectionRequest(collectionOrderRequest);


        return null;
    }

    private void validateCollectionRequest(CollectionOrderRequest collectionOrderRequest) throws PakGoPayException {

        Long userId = Long.valueOf(collectionOrderRequest.getUserId());

        MerchantInfoDto merchantInfoDto = merchantCheckUtil.getConfigurationInfo(userId);
        // check ip white list
        if (merchantCheckUtil.isColIpAllowed(userId, collectionOrderRequest.getClientIp(), merchantInfoDto.getColWhiteIps())) {
            throw new PakGoPayException(ResultCode.IS_NOT_WHITE_IP);
        }
        // check user is enabled
        if (merchantCheckUtil.isEnableMerchant(merchantInfoDto.getStatus(), merchantInfoDto.getParentId())) {
            throw new PakGoPayException(ResultCode.USER_NOT_ENABLE);
        }
        // check Merchant order code is uniqueness
        if(merchantCheckUtil.existsColMerchantOrderNo(collectionOrderRequest.getMerchantOrderNo())){
            throw new PakGoPayException(ResultCode.MERCHANT_CODE_IS_EXISTS);
        }
    }

}
