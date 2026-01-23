package com.pakgopay.service.transaction.impl;

import com.pakgopay.common.constant.CommonConstant;
import com.pakgopay.common.enums.OrderScope;
import com.pakgopay.common.enums.OrderType;
import com.pakgopay.common.enums.OrderStatus;
import com.pakgopay.common.enums.ResultCode;
import com.pakgopay.common.exception.PakGoPayException;
import com.pakgopay.data.entity.TransactionInfo;
import com.pakgopay.data.reqeust.transaction.CollectionOrderRequest;
import com.pakgopay.data.response.CommonResponse;
import com.pakgopay.mapper.CollectionOrderMapper;
import com.pakgopay.mapper.dto.CollectionOrderDto;
import com.pakgopay.mapper.dto.MerchantInfoDto;
import com.pakgopay.service.MerchantService;
import com.pakgopay.service.impl.ChannelPaymentServiceImpl;
import com.pakgopay.service.transaction.CollectionOrderService;
import com.pakgopay.service.transaction.MerchantCheckService;
import com.pakgopay.service.transaction.OrderHandler;
import com.pakgopay.service.transaction.OrderHandlerFactory;
import com.pakgopay.util.SnowflakeIdGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class CollectionOrderServiceImpl implements CollectionOrderService {

    @Autowired
    MerchantCheckService merchantCheckService;

    @Autowired
    MerchantService merchantService;

    @Autowired
    ChannelPaymentServiceImpl channelPaymentService;

    @Autowired
    private CollectionOrderMapper collectionOrderMapper;

    @Override
    public CommonResponse createCollectionOrder(CollectionOrderRequest colOrderRequest) throws PakGoPayException {
        log.info("createCollectionOrder start");
        TransactionInfo transactionInfo = new TransactionInfo();
        // 1. get merchant info
        MerchantInfoDto merchantInfoDto = merchantService.getMerchantInfo(colOrderRequest.getMerchantId());
        transactionInfo.setMerchantInfo(merchantInfoDto);
        // merchant is not exists
        if (merchantInfoDto == null) {
            log.error("merchant info is not exist, userId {}", colOrderRequest.getMerchantId());
            throw new PakGoPayException(ResultCode.USER_IS_NOT_EXIST);
        }

        // 2. check request validate
        validateCollectionRequest(colOrderRequest, merchantInfoDto);

        // 3. get available payment id
        transactionInfo.setCurrency(colOrderRequest.getCurrency());
        transactionInfo.setAmount(colOrderRequest.getAmount());
        transactionInfo.setPaymentNo(colOrderRequest.getPaymentNo());

        channelPaymentService.getPaymentId(CommonConstant.SUPPORT_TYPE_COLLECTION, transactionInfo);

        // 4. create system transaction no
        String systemTransactionNo = SnowflakeIdGenerator.getSnowFlakeId(CommonConstant.COLLECTION_PREFIX);
        log.info("generator system transactionNo :{}", systemTransactionNo);
        transactionInfo.setTransactionNo(systemTransactionNo);

        CollectionOrderDto collectionOrderDto = buildCollectionOrderDto(colOrderRequest, transactionInfo);
        Object handlerResponse = dispatchCollectionOrder(collectionOrderDto, colOrderRequest.getChannelParams());

        try {
            int ret = collectionOrderMapper.insert(collectionOrderDto);
            if (ret <= 0) {
                return CommonResponse.fail(ResultCode.DATA_BASE_ERROR, "collection order insert failed");
            }
        } catch (Exception e) {
            log.error("collection order insert failed, message {}", e.getMessage());
            throw new PakGoPayException(ResultCode.DATA_BASE_ERROR);
        }

        Map<String, Object> responseBody = buildCollectionResponse(collectionOrderDto, handlerResponse);
        return CommonResponse.success(responseBody);
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
        if(!collectionOrderDto.getMerchantUserId().equals(userId)){
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
            Long successCallBackTime = collectionOrderDto.getSuccessCallbackTime();
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
        if (!merchantCheckService.isColIpAllowed(
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
        if (!merchantCheckService.isEnableMerchant(merchantInfoDto)) {
            log.error("The merchant status is disable, merchantName: {}", merchantInfoDto.getMerchantName());
            throw new PakGoPayException(ResultCode.USER_NOT_ENABLE);
        }

        // check merchant is support collection
        if (merchantInfoDto.getSupportType() != 0 && merchantInfoDto.getSupportType() != 2) {
            log.error("The merchant is not support collection, merchantName: {}", merchantInfoDto.getMerchantName());
            throw new PakGoPayException(ResultCode.MERCHANT_NOT_SUPPORT_COLLECTION);
        }

        log.info("validateCollectionRequest success");
    }

    private CollectionOrderDto buildCollectionOrderDto(
            CollectionOrderRequest request,
            TransactionInfo transactionInfo) {
        long now = System.currentTimeMillis() / 1000;
        MerchantInfoDto merchantInfo = transactionInfo.getMerchantInfo();

        // -----------------------------
        // 1) Core identifiers & amounts
        // -----------------------------
        CollectionOrderDto dto = new CollectionOrderDto();
        dto.setTransactionNo(transactionInfo.getTransactionNo()); // system transaction no
        dto.setMerchantOrderNo(request.getMerchantOrderNo()); // merchant order no
        dto.setAmount(request.getAmount()); // requested amount
        dto.setCurrencyType(request.getCurrency()); // currency code

        // -----------------------------
        // 2) Merchant ownership linkage
        // -----------------------------
        dto.setMerchantUserId(request.getMerchantId()); // merchant user id

        // -----------------------------
        // 3) Channel/payment association
        // -----------------------------
        dto.setPaymentId(transactionInfo.getPaymentId()); // payment channel id
        dto.setChannelId(transactionInfo.getChannelId()); // channel id

        // -----------------------------
        // 4) Merchant & agent fee config
        // -----------------------------
        if (transactionInfo.getPaymentInfo() != null) {
            dto.setCollectionMode(Integer.valueOf(transactionInfo.getPaymentInfo().getIsThird())); // collection mode
        }
        if (merchantInfo != null) {
            dto.setMerchantRate(merchantInfo.getCollectionRate()); // merchant collection rate
            dto.setMerchantFixedFee(merchantInfo.getCollectionFixedFee()); // merchant fixed fee
        }
        dto.setMerchantFee(transactionInfo.getMerchantFee()); // merchant fee
        dto.setAgent1Rate(transactionInfo.getAgent1Rate()); // agent1 rate
        dto.setAgent1FixedFee(transactionInfo.getAgent1FixedFee()); // agent1 fixed fee
        dto.setAgent1Fee(transactionInfo.getAgent1Fee()); // agent1 fee
        dto.setAgent2Rate(transactionInfo.getAgent2Rate()); // agent2 rate
        dto.setAgent2FixedFee(transactionInfo.getAgent2FixedFee()); // agent2 fixed fee
        dto.setAgent2Fee(transactionInfo.getAgent2Fee()); // agent2 fee
        dto.setAgent3Rate(transactionInfo.getAgent3Rate()); // agent3 rate
        dto.setAgent3FixedFee(transactionInfo.getAgent3FixedFee()); // agent3 fixed fee
        dto.setAgent3Fee(transactionInfo.getAgent3Fee()); // agent3 fee

        // -----------------------------
        // 5) Callback & request metadata
        // -----------------------------
        dto.setOrderType(1); // order type: 1-system, 2-manual
        dto.setOrderStatus(1); // order status: 1-processing, 2-failed
        dto.setCallbackUrl(request.getNotificationUrl()); // async callback url
        dto.setCallbackTimes(CommonConstant.ZERO); // initial callback times
        dto.setRequestIp(request.getClientIp()); // request ip
        dto.setRemark(request.getRemark()); // remark
        dto.setRequestTime(now); // request time
        dto.setCreateTime(now); // create time
        dto.setUpdateTime(now); // update time

        // Unassigned fields:
        // actualAmount
        // floatingAmount
        // callbackToken
        // lastCallbackTime
        // callbackStatus
        // successCallbackTime
        return dto;
    }

    private Object dispatchCollectionOrder(CollectionOrderDto dto, Object channelParams) {
        OrderScope scope = Integer.valueOf(1).equals(dto.getCollectionMode())
                ? OrderScope.THIRD_PARTY
                : OrderScope.SYSTEM;
        OrderHandler handler = OrderHandlerFactory.get(
                OrderType.COLLECTION_ORDER, scope, dto.getCurrencyType());
        Map<String, Object> payload = new HashMap<>();
        payload.put("transactionNo", dto.getTransactionNo());
        payload.put("amount", dto.getAmount());
        payload.put("currency", dto.getCurrencyType());
        payload.put("merchantOrderNo", dto.getMerchantOrderNo());
        payload.put("merchantUserId", dto.getMerchantUserId());
        payload.put("callbackUrl", dto.getCallbackUrl());
        payload.put("channelParams", channelParams);
        return handler.handle(payload);
    }

    private Map<String, Object> buildCollectionResponse(CollectionOrderDto dto, Object handlerResponse) {
        Map<String, Object> result = new HashMap<>();
        result.put("amount", dto.getAmount());
        result.put("transactionNo", dto.getTransactionNo());
        result.put("merchantOrderNo", dto.getMerchantOrderNo());
        result.put("currency", dto.getCurrencyType());
        result.put("createTime", Instant.ofEpochSecond(dto.getCreateTime()).toString());
        result.put("status", dto.getOrderStatus());

        Map<String, Object> handlerData = extractHandlerData(handlerResponse);
        if (handlerData != null) {
            mergeIfPresent(result, handlerData, "qrCode");
            mergeIfPresent(result, handlerData, "cashierUrl");
            mergeIfPresent(result, handlerData, "payUrl");
            mergeIfPresent(result, handlerData, "status");
        }

        return result;
    }

    private Map<String, Object> extractHandlerData(Object handlerResponse) {
        if (handlerResponse == null) {
            return null;
        }
        if (handlerResponse instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        if (handlerResponse instanceof com.pakgopay.data.response.http.PaymentHttpResponse resp) {
            Object data = resp.getData();
            if (data instanceof Map<?, ?> dataMap) {
                return (Map<String, Object>) dataMap;
            }
        }
        return null;
    }

    private void mergeIfPresent(Map<String, Object> target, Map<String, Object> source, String key) {
        if (source.containsKey(key) && source.get(key) != null) {
            target.put(key, source.get(key));
        }
    }

}
