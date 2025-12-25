package com.pakgopay.service.order.impl;

import com.pakgopay.common.enums.ResultCode;
import com.pakgopay.common.reqeust.transaction.CollectionOrderRequest;
import com.pakgopay.common.response.CommonResponse;
import com.pakgopay.common.security.ConfigurationCheckUtil;
import com.pakgopay.service.order.CollectionOrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class CollectionOrderServiceImpl implements CollectionOrderService {

    @Autowired
    ConfigurationCheckUtil configurationCheckUtil;

    @Override
    public CommonResponse createCollectionOrder(CollectionOrderRequest collectionOrderRequest) {

        ResultCode resultCode = checkCollectionConfig(collectionOrderRequest);
        if (resultCode != ResultCode.SUCCESS) {
            return CommonResponse.fail(resultCode);
        }


        return null;
    }


    public ResultCode checkCollectionConfig(CollectionOrderRequest collectionOrderRequest) {
        String userId = collectionOrderRequest.getUserId();
        configurationCheckUtil.initData(userId);

        if (configurationCheckUtil.isColIpAllowed(userId, collectionOrderRequest.getClientIp())) {
            return ResultCode.NO_ROLE_INFO_FOUND;
        }

        if (configurationCheckUtil.isEnableUser(userId)) {
            return ResultCode.NO_ROLE_INFO_FOUND;
        }

        return null;
    }

}
