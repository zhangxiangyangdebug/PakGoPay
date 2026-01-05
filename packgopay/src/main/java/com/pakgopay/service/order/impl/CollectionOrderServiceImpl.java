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
        log.info("createCollectionOrder start");
        TransactionInfo transactionInfo = new TransactionInfo();
        // 1. get merchant info
        MerchantInfoDto merchantInfoDto = merchantCheckService.getMerchantInfo(colOrderRequest.getUserId());
        transactionInfo.setMerchantInfo(merchantInfoDto);
        // merchant is not exists
        if (merchantInfoDto == null) {
            log.error("merchant info is not exist, userId {}", colOrderRequest.getUserId());
            throw new PakGoPayException(ResultCode.USER_IS_NOT_EXIST);
        }

        // 2. check request validate
        validateCollectionRequest(colOrderRequest, merchantInfoDto);

        // 3. get available payment id
        transactionInfo.setCurrency(colOrderRequest.getCurrency());
        transactionInfo.setAmount(colOrderRequest.getAmount());
        transactionInfo.setPaymentNo(colOrderRequest.getPaymentNo());
        // TODO merchantInfoDto.getChannelIds()为空，取所属代理名下渠道
        Long paymentId = channelPaymentService.getPaymentId(
                merchantInfoDto.getChannelIds(), CommonConstant.SUPPORT_TYPE_COLLECTION, transactionInfo);

        // 4. create system transaction no
        String systemTransactionNo = SnowflakeIdGenerator.getSnowFlakeId(CommonConstant.COLLECTION_PREFIX);
        log.info("generator system transactionNo :{}", systemTransactionNo);
        transactionInfo.setTransactionNo(systemTransactionNo);

        return null;
    }

    @Override
    public CommonResponse queryOrderInfo(String userId, String transactionNo) throws PakGoPayException {
        log.info("queryOrderInfo start");
        // query collection order info  from db
        CollectionOrderDto collectionOrderDto;
        try {
            collectionOrderDto =
                    collectionOrderMapper.findByTransactionNo(transactionNo)
                            .orElseThrow(() -> new PakGoPayException(
                                    ResultCode.MERCHANT_ORDER_NO_NOT_EXISTS
                                    , "record is not exists, transactionNo:" + transactionNo));
        } catch (PakGoPayException e) {
            log.error("record is not exists, transactionNo {}", transactionNo);
            throw e;
        } catch (Exception e) {
            log.error("collection order findByTransactionNo failed, message {}", e.getMessage());
            throw new PakGoPayException(ResultCode.DATA_BASE_ERROR);
        }


        // check if the requester and the order owner are the same.
        if(!collectionOrderDto.getMerchantId().equals(userId)){
            log.info("the order does not belong to user");
            throw new PakGoPayException(ResultCode.ORDER_PARAM_VALID, "the order does not belong to user");
        }

        // construct return data
        Map<String, Object> result = new HashMap<>();
        result.put("merchantOrderNo", collectionOrderDto.getMerchantOrderNo());
        result.put("transactionNo", collectionOrderDto.getTransactionNo());
        result.put("amount", collectionOrderDto.getAmount());
        result.put("currency", collectionOrderDto.getCurrencyType());
        result.put("status", collectionOrderDto.getOrderStatus());
        result.put("createTime", collectionOrderDto.getCreateTime().toString());
        result.put("updateTime", collectionOrderDto.getUpdateTime().toString());

        // TODO If the transaction fails, return the reason for the failure.
        if (OrderStatus.FAILED.getCode().equals(collectionOrderDto.getOrderStatus())) {
            log.warn("order status is failed");
            result.put("failureReason", "");
        }

        if (OrderStatus.SUCCESS.getCode().equals(collectionOrderDto.getOrderStatus())) {
            LocalDateTime successCallBackTime = collectionOrderDto.getSuccessCallbackTime();
            if (successCallBackTime != null) {
                result.put("successCallBackTime", successCallBackTime.toString());
            } else {
                result.put("successCallBackTime", null);
                log.warn("The transaction status is successful, but the payment time is empty. transaction number: {}", collectionOrderDto.getMerchantOrderNo());
            }
        }

        log.info("queryOrderInfo end");
        return CommonResponse.success(result);
    }

    private void validateCollectionRequest(
            CollectionOrderRequest collectionOrderRequest, MerchantInfoDto merchantInfoDto) throws PakGoPayException {
        log.info("validateCollectionRequest start");
        // check ip white list
        if (merchantCheckService.isColIpAllowed(
                collectionOrderRequest.getUserId(), collectionOrderRequest.getClientIp(),
                merchantInfoDto.getColWhiteIps())) {
            log.error("isColIpAllowed failed, clientIp: {}", collectionOrderRequest.getClientIp());
            throw new PakGoPayException(ResultCode.IS_NOT_WHITE_IP);
        }

        // check Merchant order code is uniqueness
        if(merchantCheckService.existsColMerchantOrderNo(collectionOrderRequest.getMerchantOrderNo())){
            log.error("existsColMerchantOrderNo failed, merchantOrderNo: {}", collectionOrderRequest.getMerchantOrderNo());
            throw new PakGoPayException(ResultCode.MERCHANT_CODE_IS_EXISTS);
        }

        // amount check
        if (collectionOrderRequest.getAmount().compareTo(BigDecimal.ZERO) <= CommonConstant.ZERO) {
            log.error("The transaction amount must be greater than 0, amount: {}", collectionOrderRequest.getAmount());
            throw new PakGoPayException(ResultCode.ORDER_PARAM_VALID, "The transaction amount must be greater than 0.");
        }

        // check user is enabled
        if (merchantCheckService.isEnableMerchant(merchantInfoDto.getStatus(), merchantInfoDto.getParentId())) {
            log.error("The merchant status is disable, merchantName: {}", merchantInfoDto.getMerchantName());
            throw new PakGoPayException(ResultCode.USER_NOT_ENABLE);
        }

        // check merchant is support collection
        if (!merchantInfoDto.getCollectionEnabled()) {
            log.error("The merchant is not support collection, merchantName: {}", merchantInfoDto.getMerchantName());
            throw new PakGoPayException(ResultCode.MERCHANT_NOT_SUPPORT_COLLECTION);
        }

        log.info("validateCollectionRequest success");
    }

}
