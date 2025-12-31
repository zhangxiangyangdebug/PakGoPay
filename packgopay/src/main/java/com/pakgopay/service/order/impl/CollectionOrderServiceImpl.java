package com.pakgopay.service.order.impl;

import com.pakgopay.common.constant.CommonConstant;
import com.pakgopay.common.entity.TransactionInfo;
import com.pakgopay.common.enums.OrderStatus;
import com.pakgopay.common.enums.ResultCode;
import com.pakgopay.common.exception.PakGoPayException;
import com.pakgopay.common.reqeust.transaction.CollectionOrderRequest;
import com.pakgopay.common.response.CommonResponse;
import com.pakgopay.mapper.CollectionOrderMapper;
import com.pakgopay.mapper.dto.CollectionOrderDto;
import com.pakgopay.mapper.dto.MerchantInfoDto;
import com.pakgopay.service.order.ChannelPaymentService;
import com.pakgopay.service.order.CollectionOrderService;
import com.pakgopay.service.order.MerchantCheckService;
import com.pakgopay.util.SnowflakeIdGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class CollectionOrderServiceImpl implements CollectionOrderService {

    @Autowired
    MerchantCheckService merchantCheckService;

    @Autowired
    ChannelPaymentService channelPaymentService;

    @Autowired
    private CollectionOrderMapper collectionOrderMapper;

    @Override
    public CommonResponse createCollectionOrder(CollectionOrderRequest colOrderRequest) throws PakGoPayException {
        TransactionInfo transactionInfo = new TransactionInfo();
        // 1. get merchant info
        MerchantInfoDto merchantInfoDto = merchantCheckService.getConfigurationInfo(colOrderRequest.getUserId());
        transactionInfo.setMerchantInfo(merchantInfoDto);
        // merchant is not exists
        if (merchantInfoDto == null) {
            throw new PakGoPayException(ResultCode.USER_IS_NOT_EXIST);
        }

        // 2. check request validate
        validateCollectionRequest(colOrderRequest, merchantInfoDto);

        // 3. get available payment id
        transactionInfo.setCurrency(colOrderRequest.getCurrency());
        transactionInfo.setAmount(colOrderRequest.getAmount());
        transactionInfo.setPaymentNo(colOrderRequest.getPaymentNo());
        Long paymentId = channelPaymentService.getPaymentId(
                merchantInfoDto.getChannelIds(), CommonConstant.SUPPORT_TYPE_COLLECTION, transactionInfo);

        // 4. create system transaction no
        String systemTransactionNo = SnowflakeIdGenerator.getSnowFlakeId(CommonConstant.COLLECTION_PREFIX);
        transactionInfo.setOrderId(systemTransactionNo);

        return null;
    }

    @Override
    public CommonResponse queryOrderInfo(String userId, String merchantOrderNo) throws PakGoPayException {

        // query collection order info  from db
        CollectionOrderDto collectionOrderDto =
                collectionOrderMapper.findByOrderId(merchantOrderNo)
                        .orElseThrow(() -> new PakGoPayException(ResultCode.MERCHANT_ORDER_NO_NOT_EXISTS));

        // check if the requester and the order owner are the same.
        if(!collectionOrderDto.getMerchantId().equals(userId)){
            throw new PakGoPayException(ResultCode.ORDER_PARAM_VALID, "the order does not belong to user");
        }

        // construct return data
        Map<String, Object> result = new HashMap<>();
        result.put("transactionNo", collectionOrderDto.getMerchantOrderNo());
        result.put("merchantOrderNo", collectionOrderDto.getOrderId());
        result.put("amount", collectionOrderDto.getAmount());
        result.put("currency", collectionOrderDto.getCurrencyType());
        result.put("status", collectionOrderDto.getOrderStatus());
        result.put("createTime", collectionOrderDto.getCreateTime().toString());
        result.put("updateTime", collectionOrderDto.getUpdateTime().toString());

        // TODO If the transaction fails, return the reason for the failure.
        if (OrderStatus.FAILED.getCode().equals(collectionOrderDto.getOrderStatus())) {
            result.put("failureReason", "");
        }

        if (OrderStatus.SUCCESS.getCode().equals(collectionOrderDto.getOrderStatus())) {
            LocalDateTime successCallBackTime = collectionOrderDto.getSuccessCallbackTime();
            if (successCallBackTime != null) {
                result.put("successCallBackTime", successCallBackTime.toString());
            } else {
                // 可以选择不添加 payTime 字段，或者添加 null 值
                result.put("successCallBackTime", null);
                // 或者记录警告日志
                log.warn("The transaction status is successful, but the payment time is empty. transaction number: {}", collectionOrderDto.getMerchantOrderNo());
            }
        }

        return CommonResponse.success(result);
    }

    private void validateCollectionRequest(
            CollectionOrderRequest collectionOrderRequest, MerchantInfoDto merchantInfoDto) throws PakGoPayException {
        // check ip white list
        if (merchantCheckService.isColIpAllowed(
                collectionOrderRequest.getUserId(), collectionOrderRequest.getClientIp(),
                merchantInfoDto.getColWhiteIps())) {
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

        // check merchant is support collection
        if (!merchantInfoDto.getCollectionEnabled()) {
            throw new PakGoPayException(ResultCode.MERCHANT_NOT_SUPPORT_COLLECTION);
        }
    }

}
