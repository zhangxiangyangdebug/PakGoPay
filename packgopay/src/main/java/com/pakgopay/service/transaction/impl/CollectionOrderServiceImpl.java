package com.pakgopay.service.transaction.impl;

import com.pakgopay.common.constant.CommonConstant;
import com.pakgopay.common.enums.*;
import com.pakgopay.common.exception.PakGoPayException;
import com.pakgopay.data.entity.TransactionInfo;
import com.pakgopay.data.reqeust.transaction.CollectionOrderRequest;
import com.pakgopay.data.reqeust.transaction.NotifyRequest;
import com.pakgopay.data.response.CommonResponse;
import com.pakgopay.data.response.http.PaymentHttpResponse;
import com.pakgopay.mapper.CollectionOrderMapper;
import com.pakgopay.mapper.dto.CollectionOrderDto;
import com.pakgopay.mapper.dto.MerchantInfoDto;
import com.pakgopay.service.BalanceService;
import com.pakgopay.service.ChannelPaymentService;
import com.pakgopay.service.MerchantService;
import com.pakgopay.service.transaction.CollectionOrderService;
import com.pakgopay.service.transaction.MerchantCheckService;
import com.pakgopay.service.transaction.OrderHandler;
import com.pakgopay.service.transaction.OrderHandlerFactory;
import com.pakgopay.util.CommontUtil;
import com.pakgopay.util.PatchBuilderUtil;
import com.pakgopay.util.SnowflakeIdGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
public class CollectionOrderServiceImpl implements CollectionOrderService {

    @Autowired
    MerchantCheckService merchantCheckService;

    @Autowired
    MerchantService merchantService;

    @Autowired
    ChannelPaymentService channelPaymentService;

    @Autowired
    private BalanceService balanceService;

    @Autowired
    private CollectionOrderMapper collectionOrderMapper;

    @Override
    public CommonResponse createCollectionOrder(CollectionOrderRequest colOrderRequest) throws PakGoPayException {
        log.info("createCollectionOrder start, merchantId={}, merchantOrderNo={}, currency={}, amount={}, paymentNo={}",
                colOrderRequest.getMerchantId(),
                colOrderRequest.getMerchantOrderNo(),
                colOrderRequest.getCurrency(),
                colOrderRequest.getAmount(),
                colOrderRequest.getPaymentNo());
        TransactionInfo transactionInfo = new TransactionInfo();
        // 1. get merchant info
        MerchantInfoDto merchantInfoDto = merchantService.fetchMerchantInfo(colOrderRequest.getMerchantId());
        transactionInfo.setMerchantInfo(merchantInfoDto);
        // merchant is not exists
        if (merchantInfoDto == null) {
            log.error("merchant info is not exist, userId {}", colOrderRequest.getMerchantId());
            throw new PakGoPayException(ResultCode.USER_IS_NOT_EXIST);
        }
        log.info("merchant info loaded, userId={}, agentId={}, channelIds={}",
                merchantInfoDto.getUserId(),
                merchantInfoDto.getParentId(),
                merchantInfoDto.getChannelIds());

        // 2. check request validate
        validateCollectionRequest(colOrderRequest, merchantInfoDto);
        log.info("collection request validated, merchantId={}", colOrderRequest.getMerchantId());

        // 3. get available payment id
        transactionInfo.setCurrency(colOrderRequest.getCurrency());
        transactionInfo.setAmount(colOrderRequest.getAmount());
        transactionInfo.setPaymentNo(colOrderRequest.getPaymentNo());

        channelPaymentService.selectPaymentId(CommonConstant.SUPPORT_TYPE_COLLECTION, transactionInfo);
        log.info("payment resolved, paymentId={}, channelId={}",
                transactionInfo.getPaymentId(),
                transactionInfo.getChannelId());

        // 4. create system transaction no
        String systemTransactionNo = SnowflakeIdGenerator.getSnowFlakeId(CommonConstant.COLLECTION_PREFIX);
        log.info("generator system transactionNo :{}", systemTransactionNo);
        transactionInfo.setTransactionNo(systemTransactionNo);

        CollectionOrderDto collectionOrderDto = buildCollectionOrderDto(colOrderRequest, transactionInfo);
        log.info("collectionOrderDto built, transactionNo={}, paymentId={}, channelId={}, collectionMode={}",
                collectionOrderDto.getTransactionNo(),
                collectionOrderDto.getPaymentId(),
                collectionOrderDto.getChannelId(),
                collectionOrderDto.getCollectionMode());
        Object handlerResponse = dispatchCollectionOrder(collectionOrderDto, colOrderRequest.getChannelParams());
        log.info("collection handler dispatched, transactionNo={}, responseType={}",
                collectionOrderDto.getTransactionNo(),
                handlerResponse == null ? null : handlerResponse.getClass().getSimpleName());

        try {
            int ret = collectionOrderMapper.insert(collectionOrderDto);
            if (ret <= 0) {
                return CommonResponse.fail(ResultCode.DATA_BASE_ERROR, "collection order insert failed");
            }
            log.info("collection order inserted, transactionNo={}", collectionOrderDto.getTransactionNo());
        } catch (Exception e) {
            log.error("collection order insert failed, message {}", e.getMessage());
            throw new PakGoPayException(ResultCode.DATA_BASE_ERROR);
        }

        Map<String, Object> responseBody = buildCollectionResponse(collectionOrderDto, handlerResponse);
        log.info("createCollectionOrder success, transactionNo={}", collectionOrderDto.getTransactionNo());
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

    @Override
    public CommonResponse handleNotify(String currency, String body) throws PakGoPayException {
        log.info("handleNotify start, currency={}, bodySize={}", currency, body == null ? 0 : body.length());
        OrderHandler handler = OrderHandlerFactory.get(
                OrderType.COLLECTION_ORDER, OrderScope.THIRD_PARTY, currency);

        // Parse notify payload into a structured response.
        NotifyRequest response = handler.handleNotify(body);
        log.info("notify parsed, transactionNo={}, merchantNo={}, status={}",
                response.getTransactionNo(), response.getMerchantNo(),
                response.getStatus());

        // Validate required fields in the notify response.
        OrderHandler.validateNotifyResponse(response);
        log.info("notify response validated, transactionNo={}", response.getTransactionNo());

        // Verify order existence and validate state transition.
        CollectionOrderDto collectionOrderDto = validateNotifyOrder(response);
        log.info("notify order validated, transactionNo={}, orderStatus={}",
                collectionOrderDto.getTransactionNo(),
                collectionOrderDto.getOrderStatus());

        // Calculate fees, update order record, and apply balance changes.
        applyNotifyUpdate(collectionOrderDto, response);
        log.info("notify applied, transactionNo={}, status={}",
                collectionOrderDto.getTransactionNo(),
                response.getStatus());

        return CommonResponse.success(response);
    }

    private CollectionOrderDto validateNotifyOrder(NotifyRequest response) throws PakGoPayException {
        CollectionOrderDto collectionOrderDto = collectionOrderMapper.findByTransactionNo(response.getTransactionNo())
                .orElseThrow(() -> new PakGoPayException(ResultCode.MERCHANT_ORDER_NO_NOT_EXISTS,
                        "record is not exists, transactionNo:" + response.getTransactionNo()));
        if (collectionOrderDto.getMerchantUserId() == null
                || !collectionOrderDto.getMerchantUserId().equals(response.getMerchantNo())) {
            throw new PakGoPayException(ResultCode.ORDER_PARAM_VALID, "merchantNo does not match");
        }
        if (TransactionStatus.SUCCESS.getCode().equals(collectionOrderDto.getOrderStatus())
                || TransactionStatus.FAILED.getCode().equals(collectionOrderDto.getOrderStatus())) {
            throw new PakGoPayException(ResultCode.ORDER_PARAM_VALID, "order status can not be changed");
        }
        return collectionOrderDto;
    }

    private void applyNotifyUpdate(CollectionOrderDto collectionOrderDto, NotifyRequest response) throws PakGoPayException {
        TransactionStatus targetStatus = resolveNotifyStatus(response.getStatus());
        MerchantInfoDto merchantInfo = merchantService.fetchMerchantInfo(collectionOrderDto.getMerchantUserId());
        if (merchantInfo == null) {
            throw new PakGoPayException(ResultCode.USER_IS_NOT_EXIST);
        }
        TransactionInfo transactionInfo = new TransactionInfo();
        transactionInfo.setMerchantInfo(merchantInfo);
        transactionInfo.setAmount(resolveOrderAmount(collectionOrderDto));
        channelPaymentService.calculateTransactionFees(transactionInfo, OrderType.COLLECTION_ORDER);

        CollectionOrderDto update = new CollectionOrderDto();
        update.setTransactionNo(collectionOrderDto.getTransactionNo());
        update.setMerchantRate(transactionInfo.getMerchantRate());
        update.setMerchantFixedFee(transactionInfo.getMerchantFixedFee());
        update.setMerchantFee(transactionInfo.getMerchantFee());
        update.setAgent1Rate(transactionInfo.getAgent1Rate());
        update.setAgent1FixedFee(transactionInfo.getAgent1FixedFee());
        update.setAgent1Fee(transactionInfo.getAgent1Fee());
        update.setAgent2Rate(transactionInfo.getAgent2Rate());
        update.setAgent2FixedFee(transactionInfo.getAgent2FixedFee());
        update.setAgent2Fee(transactionInfo.getAgent2Fee());
        update.setAgent3Rate(transactionInfo.getAgent3Rate());
        update.setAgent3FixedFee(transactionInfo.getAgent3FixedFee());
        update.setAgent3Fee(transactionInfo.getAgent3Fee());
        update.setOrderStatus(targetStatus.getCode());
        if (TransactionStatus.SUCCESS.equals(targetStatus)) {
            update.setSuccessCallbackTime(System.currentTimeMillis() / 1000);
        }
        update.setUpdateTime(System.currentTimeMillis() / 1000);
        try {
            collectionOrderMapper.updateByTransactionNo(update);
        channelPaymentService.updateChannelAndPaymentCounters(collectionOrderDto, targetStatus);
        } catch (Exception e) {
            log.error("collection order updateByTransactionNo failed, message {}", e.getMessage());
            throw new PakGoPayException(ResultCode.DATA_BASE_ERROR);
        }

        if (TransactionStatus.SUCCESS.equals(targetStatus)) {
            BigDecimal creditAmount = CommontUtil.safeSubtract(
                    resolveOrderAmount(collectionOrderDto), transactionInfo.getMerchantFee());
//            balanceService.createBalanceRecord(collectionOrderDto.getMerchantUserId(), collectionOrderDto.getCurrencyType());
            balanceService.creditBalance(
                    collectionOrderDto.getMerchantUserId(),
                    collectionOrderDto.getCurrencyType(),
                    creditAmount);
        }
    }

    private TransactionStatus resolveNotifyStatus(String status) throws PakGoPayException {
        if (status == null || status.isBlank()) {
            throw new PakGoPayException(ResultCode.ORDER_PARAM_VALID, "status is empty");
        }
        if (TransactionStatus.SUCCESS.getMessage().equalsIgnoreCase(status)) {
            return TransactionStatus.SUCCESS;
        }
        if (TransactionStatus.FAILED.getMessage().equalsIgnoreCase(status)) {
            return TransactionStatus.FAILED;
        }
        if (TransactionStatus.PROCESSING.getMessage().equalsIgnoreCase(status)) {
            return TransactionStatus.PROCESSING;
        }
        if (TransactionStatus.PENDING.getMessage().equalsIgnoreCase(status)) {
            return TransactionStatus.PENDING;
        }
        if (TransactionStatus.EXPIRED.getMessage().equalsIgnoreCase(status)) {
            return TransactionStatus.EXPIRED;
        }
        if (TransactionStatus.CANCELLED.getMessage().equalsIgnoreCase(status)) {
            return TransactionStatus.CANCELLED;
        }
        throw new PakGoPayException(ResultCode.ORDER_PARAM_VALID, "unsupported status");
    }

    private BigDecimal resolveOrderAmount(CollectionOrderDto dto) {
        if (dto.getActualAmount() != null) {
            return dto.getActualAmount();
        }
        return dto.getAmount();
    }

    private void validateCollectionRequest(
            CollectionOrderRequest collectionOrderRequest, MerchantInfoDto merchantInfoDto) throws PakGoPayException {
        log.info("validateCollectionRequest start");
        // check ip white list
        if (!merchantCheckService.isColIpAllowed(collectionOrderRequest.getClientIp(),
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
        PatchBuilderUtil<CollectionOrderRequest, CollectionOrderDto> builder = PatchBuilderUtil.from(request).to(dto);
        builder.obj(transactionInfo::getTransactionNo, dto::setTransactionNo) // system transaction no
                .obj(request::getMerchantOrderNo, dto::setMerchantOrderNo) // merchant order no
                .obj(request::getAmount, dto::setAmount) // requested amount
                .obj(request::getCurrency, dto::setCurrencyType); // currency code
        if (merchantInfo.getIsFloat() == 1) {
            BigDecimal floatingAmount = generateFloatAmount(merchantInfo.getFloatRange());
            builder.obj(() -> floatingAmount, dto::setFloatingAmount) // floating amount
                    .obj(() -> CommontUtil.safeSubtract(request.getAmount(), floatingAmount), dto::setActualAmount); // floating amount
        }

        // -----------------------------
        // 2) Merchant ownership linkage
        // -----------------------------
        builder.obj(request::getMerchantId, dto::setMerchantUserId); // merchant user id

        // -----------------------------
        // 3) Channel/payment association
        // -----------------------------
        builder.obj(transactionInfo::getPaymentId, dto::setPaymentId) // payment channel id
                .obj(transactionInfo::getChannelId, dto::setChannelId); // channel id

        // -----------------------------
        // 4) Merchant & agent fee config
        // -----------------------------
        if (transactionInfo.getPaymentInfo() != null) {
            Integer collectionMode = "1".equals(transactionInfo.getPaymentInfo().getIsThird()) ? 2 : 1;
            builder.obj(() -> collectionMode, dto::setCollectionMode); // collection mode
        }
        if (merchantInfo != null) {
            builder.obj(merchantInfo::getCollectionRate, dto::setMerchantRate) // merchant collection rate
                    .obj(merchantInfo::getCollectionFixedFee, dto::setMerchantFixedFee); // merchant fixed fee
        }
        builder.obj(transactionInfo::getMerchantFee, dto::setMerchantFee) // merchant fee
                .obj(transactionInfo::getAgent1Rate, dto::setAgent1Rate) // agent1 rate
                .obj(transactionInfo::getAgent1FixedFee, dto::setAgent1FixedFee) // agent1 fixed fee
                .obj(transactionInfo::getAgent1Fee, dto::setAgent1Fee) // agent1 fee
                .obj(transactionInfo::getAgent2Rate, dto::setAgent2Rate) // agent2 rate
                .obj(transactionInfo::getAgent2FixedFee, dto::setAgent2FixedFee) // agent2 fixed fee
                .obj(transactionInfo::getAgent2Fee, dto::setAgent2Fee) // agent2 fee
                .obj(transactionInfo::getAgent3Rate, dto::setAgent3Rate) // agent3 rate
                .obj(transactionInfo::getAgent3FixedFee, dto::setAgent3FixedFee) // agent3 fixed fee
                .obj(transactionInfo::getAgent3Fee, dto::setAgent3Fee); // agent3 fee

        // -----------------------------
        // 5) Callback & request metadata
        // -----------------------------
        builder.obj(() -> 1, dto::setOrderType) // order type: 1-system, 2-manual
                .obj(() -> 1, dto::setOrderStatus) // order status: 1-processing, 2-failed
                .obj(request::getNotificationUrl, dto::setCallbackUrl) // async callback url
                .obj(() -> CommonConstant.ZERO, dto::setCallbackTimes) // initial callback times
                .obj(request::getClientIp, dto::setRequestIp) // request ip
                .obj(request::getRemark, dto::setRemark) // remark
                .obj(() -> now, dto::setRequestTime) // request time
                .obj(() -> now, dto::setCreateTime) // create time
                .obj(() -> now, dto::setUpdateTime); // update time

        // Unassigned fields:
        // actualAmount
        // callbackToken
        // lastCallbackTime
        // callbackStatus
        // successCallbackTime
        return builder.build();
    }

    private BigDecimal generateFloatAmount(BigDecimal floatRange) {
        if (floatRange == null || floatRange.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        double value = ThreadLocalRandom.current().nextDouble(0.0d, floatRange.doubleValue());
        return BigDecimal.valueOf(value);
    }

    private Object dispatchCollectionOrder(CollectionOrderDto dto, Object channelParams) {
        OrderScope scope = Integer.valueOf(1).equals(dto.getCollectionMode())
                ? OrderScope.THIRD_PARTY
                : OrderScope.SYSTEM;
        OrderHandler handler = OrderHandlerFactory.get(
                OrderType.COLLECTION_ORDER, scope, dto.getCurrencyType());
        Map<String, Object> payload = new HashMap<>();
        payload.put("transactionNo", dto.getTransactionNo());
        // TODO 需要查看为什么为null
        payload.put("amount", dto.getActualAmount() != null ? dto.getActualAmount() : new BigDecimal("1"));
        payload.put("currency", dto.getCurrencyType());
        payload.put("merchantOrderNo", dto.getMerchantOrderNo());
        payload.put("merchantUserId", dto.getMerchantUserId());
        payload.put("callbackUrl", dto.getCallbackUrl());
        // TODO 待处理，确定使用那个通道
        payload.put("channelCode", "digimone");
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
        if (handlerResponse instanceof PaymentHttpResponse resp) {
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
