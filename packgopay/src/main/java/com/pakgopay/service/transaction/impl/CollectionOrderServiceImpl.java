package com.pakgopay.service.transaction.impl;

import com.pakgopay.common.constant.CommonConstant;
import com.pakgopay.common.enums.*;
import com.pakgopay.common.exception.PakGoPayException;
import com.pakgopay.data.entity.OrderQueryEntity;
import com.pakgopay.data.entity.TransactionInfo;
import com.pakgopay.data.entity.transaction.CollectionCreateEntity;
import com.pakgopay.data.entity.transaction.CollectionQueryEntity;
import com.pakgopay.data.reqeust.transaction.CollectionOrderRequest;
import com.pakgopay.data.reqeust.transaction.NotifyRequest;
import com.pakgopay.data.reqeust.transaction.OrderQueryRequest;
import com.pakgopay.data.response.CollectionOrderPageResponse;
import com.pakgopay.data.response.CommonResponse;
import com.pakgopay.mapper.CollectionOrderMapper;
import com.pakgopay.mapper.dto.CollectionOrderDto;
import com.pakgopay.mapper.dto.MerchantInfoDto;
import com.pakgopay.mapper.dto.PaymentDto;
import com.pakgopay.service.BalanceService;
import com.pakgopay.service.ChannelPaymentService;
import com.pakgopay.service.MerchantService;
import com.pakgopay.service.transaction.*;
import com.pakgopay.util.*;
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
public class CollectionOrderServiceImpl extends BaseOrderService implements CollectionOrderService {

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

    @Autowired
    private TransactionUtil transactionUtil;

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

        // 4. prepare amount and fee snapshot
        BigDecimal actualAmount = colOrderRequest.getAmount();
        if (merchantInfoDto.getIsFloat() == 1) {
            BigDecimal floatingAmount = generateFloatAmount(merchantInfoDto.getFloatRange());
            actualAmount = CalcUtil.safeSubtract(colOrderRequest.getAmount(), floatingAmount);
        }
        transactionInfo.setActualAmount(actualAmount);
        channelPaymentService.calculateTransactionFees(transactionInfo, OrderType.COLLECTION_ORDER);
        log.info("fee calculated, merchantFee={}, agent1Fee={}, agent2Fee={}, agent3Fee={}",
                transactionInfo.getMerchantFee(),
                transactionInfo.getAgent1Fee(),
                transactionInfo.getAgent2Fee(),
                transactionInfo.getAgent3Fee());

        // 5. create system transaction no
        String systemTransactionNo = SnowflakeIdGenerator.getSnowFlakeId(CommonConstant.COLLECTION_PREFIX);
        log.info("generator system transactionNo :{}", systemTransactionNo);
        transactionInfo.setTransactionNo(systemTransactionNo);

        CollectionOrderDto collectionOrderDto = buildCollectionOrderDto(colOrderRequest, transactionInfo);
        log.info("collectionOrderDto built, transactionNo={}, paymentId={}, channelId={}, collectionMode={}",
                collectionOrderDto.getTransactionNo(),
                collectionOrderDto.getPaymentId(),
                collectionOrderDto.getChannelId(),
                collectionOrderDto.getCollectionMode());
        Object handlerResponse = dispatchCollectionOrder(
                collectionOrderDto,
                transactionInfo.getPaymentInfo(),
                colOrderRequest.getChannelParams());
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
        if (OrderStatus.FAILED.getCode().toString().equals(collectionOrderDto.getOrderStatus())) {
            log.warn("order status is failed");
            result.put("failureReason", "");
        }

        if (OrderStatus.SUCCESS.getCode().toString().equals(collectionOrderDto.getOrderStatus())) {
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
    public Object handleNotify(Map<String, Object> notifyData) throws PakGoPayException {
        log.info("handleNotify start, notifyData: {}", notifyData);
        // Verify order existence and validate state transition.
        CollectionOrderDto collectionOrderDto = validateNotifyOrder(notifyData);
        log.info("notify order validated, transactionNo={}, orderStatus={}",
                collectionOrderDto.getTransactionNo(),
                collectionOrderDto.getOrderStatus());

        OrderScope scope = Integer.valueOf(1).equals(collectionOrderDto.getCollectionMode())
                ? OrderScope.THIRD_PARTY
                : OrderScope.SYSTEM;
        OrderHandler handler = OrderHandlerFactory.get(
                scope, collectionOrderDto.getCurrencyType(), collectionOrderDto.getPaymentNo());

        // Query payment config early for subsequent channel query.
        PaymentDto paymentDto = fetchPaymentById(collectionOrderDto.getPaymentId());

        NotifyRequest response = null;
        TransactionStatus targetStatus = TransactionStatus.FAILED;
        try {
            // Parse notify payload into a structured response.
            response = handler.handleNotify(notifyData, collectionOrderDto.getCallbackToken());
            log.info("notify parsed, transactionNo={}, merchantNo={}, status={}",
                    response.getTransactionNo(), response.getMerchantNo(),
                    response.getStatus());

            // Validate required fields in the notify response.
            OrderHandler.validateNotifyResponse(response);
            log.info("notify response validated, transactionNo={}", response.getTransactionNo());

            TransactionStatus notifyStatus = resolveNotifyStatus(response.getStatus());
            if (TransactionStatus.FAILED.equals(notifyStatus)) {
                log.warn("notify status is FAILED, transactionNo={}", collectionOrderDto.getTransactionNo());
                targetStatus = TransactionStatus.FAILED;
            } else {
                // Query order status from channel and use it as the target status.
                targetStatus = queryOrderTargetStatus(collectionOrderDto, paymentDto, handler);
            }
        } catch (Exception e) {
            log.warn("handleNotify pre-check failed, transactionNo={}, message={}",
                    collectionOrderDto.getTransactionNo(), e.getMessage());
            targetStatus = TransactionStatus.FAILED;
        }

        // Calculate fees, update order record, and apply balance changes.
        applyNotifyUpdate(collectionOrderDto, targetStatus);
        log.info("notify applied, transactionNo={}, targetStatus={}",
                collectionOrderDto.getTransactionNo(), targetStatus);

        // Send final notify payload to merchant.
        if (collectionOrderDto.getCallbackUrl() != null && !collectionOrderDto.getCallbackUrl().isBlank()) {
            OrderHandler.NotifyResult notifyResult = handler.sendNotifyToMerchant(
                    buildCollectionNotifyBody(collectionOrderDto, targetStatus, collectionOrderDto.getCallbackToken()),
                    collectionOrderDto.getCallbackUrl());
            updateNotifyCallbackMeta(collectionOrderDto, notifyResult);
        }

        refreshReportData(collectionOrderDto.getCreateTime(),collectionOrderDto.getCurrencyType());

        return handler.getNotifySuccessResponse();
    }

    private void updateNotifyCallbackMeta(CollectionOrderDto orderDto, OrderHandler.NotifyResult notifyResult) {
        long now = System.currentTimeMillis() / 1000;
        boolean success = notifyResult != null && notifyResult.isSuccess();
        int failedAttempts = notifyResult == null ? 1 : Math.max(notifyResult.getFailedAttempts(), 0);
        int totalAttempts = failedAttempts + (success ? 1 : 0);
        try {
            collectionOrderMapper.increaseCallbackTimes(
                    orderDto.getTransactionNo(),
                    now,
                    totalAttempts,
                    success ? now : null);
            log.info("merchant notify callback meta updated, transactionNo={}, totalAttempts={}, success={}",
                    orderDto.getTransactionNo(), totalAttempts, success);
        } catch (Exception e) {
            log.error("update notify callback meta failed, transactionNo={}, message={}",
                    orderDto.getTransactionNo(), e.getMessage());
        }
    }

    @Override
    public CommonResponse queryCollectionOrders(OrderQueryRequest request) throws PakGoPayException {
        log.info("queryCollectionOrders start, merchantUserId={}, transactionNo={}, merchantOrderNo={}",
                request.getMerchantUserId(), request.getTransactionNo(), request.getMerchantOrderNo());
        OrderQueryEntity entity = buildOrderQueryEntity(request);

        CollectionOrderPageResponse response = new CollectionOrderPageResponse();
        try {
            Integer totalNumber = collectionOrderMapper.countByQuery(entity);
            response.setTotalNumber(totalNumber);
            response.setCollectionOrderDtoList(collectionOrderMapper.pageByQuery(entity));
        } catch (Exception e) {
            log.error("queryCollectionOrders failed, message {}", e.getMessage());
            throw new PakGoPayException(ResultCode.DATA_BASE_ERROR);
        }
        response.setPageNo(entity.getPageNo());
        response.setPageSize(entity.getPageSize());
        log.info("queryCollectionOrders end, totalNumber={}", response.getTotalNumber());
        return CommonResponse.success(response);
    }

    private CollectionOrderDto validateNotifyOrder(Map<String, Object> notifyData) throws PakGoPayException {
        String transactionNo = extractTransactionNo(notifyData);
        CollectionOrderDto collectionOrderDto = collectionOrderMapper.findByTransactionNo(transactionNo)
                .orElseThrow(() -> new PakGoPayException(ResultCode.MERCHANT_ORDER_NO_NOT_EXISTS,
                        "record is not exists, transactionNo:" + transactionNo));
        if (TransactionStatus.SUCCESS.getCode().toString().equals(collectionOrderDto.getOrderStatus())
                || TransactionStatus.FAILED.getCode().toString().equals(collectionOrderDto.getOrderStatus())) {
            throw new PakGoPayException(ResultCode.ORDER_PARAM_VALID, "order status can not be changed");
        }
        return collectionOrderDto;
    }

    private void applyNotifyUpdate(
            CollectionOrderDto collectionOrderDto,
            TransactionStatus targetStatus) throws PakGoPayException {
        MerchantInfoDto merchantInfo = merchantService.fetchMerchantInfo(collectionOrderDto.getMerchantUserId());
        if (merchantInfo == null) {
            throw new PakGoPayException(ResultCode.USER_IS_NOT_EXIST);
        }

        CollectionOrderDto update = new CollectionOrderDto();
        update.setTransactionNo(collectionOrderDto.getTransactionNo());
        update.setOrderStatus(targetStatus.getCode().toString());
        update.setOperateType("SYSTEM");
        if (TransactionStatus.SUCCESS.equals(targetStatus)) {
            update.setSuccessCallbackTime(System.currentTimeMillis() / 1000);
        }
        update.setUpdateTime(System.currentTimeMillis() / 1000);

        transactionUtil.runInTransaction(() -> {
            try {
                int updated = collectionOrderMapper.updateByTransactionNoWhenProcessing(
                        update, TransactionStatus.PROCESSING.getCode().toString());
                if (updated <= 0) {
                    log.info("collection notify skipped, order not processing, transactionNo={}, status={}",
                            collectionOrderDto.getTransactionNo(), targetStatus.getCode());
                    return;
                }
                channelPaymentService.updateChannelAndPaymentCounters(collectionOrderDto, targetStatus);
            } catch (Exception e) {
                log.error("collection order updateByTransactionNo failed, message {}", e.getMessage());
                throw new PakGoPayException(ResultCode.DATA_BASE_ERROR);
            }

            if (TransactionStatus.SUCCESS.equals(targetStatus)) {
                BigDecimal creditAmount = CalcUtil.safeSubtract(
                        resolveOrderAmount(collectionOrderDto.getActualAmount(), collectionOrderDto.getAmount()),
                        collectionOrderDto.getMerchantFee());
                CommonUtil.withBalanceLogContext("collection.handleNotify", collectionOrderDto.getTransactionNo(), () -> {
                    balanceService.creditBalance(
                            collectionOrderDto.getMerchantUserId(),
                            collectionOrderDto.getCurrencyType(),
                            creditAmount);
                });
                updateAgentFeeBalance(balanceService, merchantInfo, collectionOrderDto.getCurrencyType(),
                        collectionOrderDto.getAgent1Fee(),
                        collectionOrderDto.getAgent2Fee(),
                        collectionOrderDto.getAgent3Fee());
            }
        });
    }

    private TransactionStatus queryOrderTargetStatus(
            CollectionOrderDto collectionOrderDto, PaymentDto paymentDto, OrderHandler handler) {
        if (handler == null || collectionOrderDto == null) {
            log.warn("queryOrderTargetStatus fallback FAILED, reason=handler_or_order_null, transactionNo={}",
                    collectionOrderDto == null ? null : collectionOrderDto.getTransactionNo());
            return TransactionStatus.FAILED;
        }
        try {
            CollectionQueryEntity query = new CollectionQueryEntity();
            query.setTransactionNo(collectionOrderDto.getTransactionNo());
            query.setSign(collectionOrderDto.getCallbackToken());
            query.setPaymentCheckCollectionUrl(paymentDto.getPaymentCheckCollectionUrl());
            TransactionStatus queryStatus = handler.handleCollectionQuery(query);
            log.info("queryOrderTargetStatus result, transactionNo={}, queryStatus={}",
                    collectionOrderDto.getTransactionNo(), queryStatus);
            return queryStatus;
        } catch (PakGoPayException e) {
            throw e;
        } catch (Exception e) {
            log.warn("queryOrderTargetStatus failed, transactionNo={}, message={}",
                    collectionOrderDto.getTransactionNo(), e.getMessage());
            return TransactionStatus.FAILED;
        }
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
        BigDecimal actualAmount = transactionInfo.getActualAmount();
        builder.obj(() -> actualAmount, dto::setActualAmount); // actual amount
        if (merchantInfo.getIsFloat() == 1) {
            BigDecimal floatingAmount = CalcUtil.safeSubtract(request.getAmount(), actualAmount);
            builder.obj(() -> floatingAmount, dto::setFloatingAmount); // floating amount
        }

        // -----------------------------
        // 2) Merchant ownership linkage
        // -----------------------------
        builder.obj(request::getMerchantId, dto::setMerchantUserId); // merchant user id

        // -----------------------------
        // 3) Channel/payment association
        // -----------------------------
        builder.obj(transactionInfo::getPaymentId, dto::setPaymentId) // payment channel id
                .obj(transactionInfo::getPaymentNo, dto::setPaymentNo) // payment channel no
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
                .obj(() -> OrderStatus.PROCESSING.getCode().toString(), dto::setOrderStatus) // order status: 1-processing, 2-failed
                .obj(request::getNotificationUrl, dto::setCallbackUrl) // async callback url
                //TODO 商户的加密密钥
//                .obj(merchantInfo::get, dto::setCallbackToken) // async callback url
                .obj(() -> CommonConstant.ZERO, dto::setCallbackTimes) // initial callback times
                .obj(request::getClientIp, dto::setRequestIp) // request ip
                .obj(request::getRemark, dto::setRemark) // remark
                .obj(() -> now, dto::setRequestTime) // request time
                .obj(() -> now, dto::setCreateTime) // create time
                .obj(() -> now, dto::setUpdateTime); // update time

        // Unassigned fields:
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

    private Object dispatchCollectionOrder(CollectionOrderDto dto, PaymentDto paymentInfo, Object channelParams) {
        OrderScope scope = Integer.valueOf(1).equals(dto.getCollectionMode())
                ? OrderScope.THIRD_PARTY
                : OrderScope.SYSTEM;
        String channelCode = resolveChannelCode(channelParams);
        OrderHandler handler = OrderHandlerFactory.get(
                scope, resolveCurrencyKey(dto.getCurrencyType(), channelCode), dto.getPaymentNo());
        CollectionCreateEntity entity = new CollectionCreateEntity();
        entity.setTransactionNo(dto.getTransactionNo());
        // TODO 需要查看为什么为null
        entity.setAmount(dto.getActualAmount() != null ? dto.getActualAmount() : new BigDecimal("1"));
        entity.setMerchantOrderNo(dto.getTransactionNo());
        entity.setMerchantUserId(dto.getMerchantUserId());
        entity.setCallbackUrl(paymentInfo == null ? null : paymentInfo.getCollectionCallbackAddr());
        entity.setIp(dto.getRequestIp());
        entity.setPaymentRequestCollectionUrl(
                paymentInfo == null ? null : paymentInfo.getPaymentRequestCollectionUrl());
        entity.setCollectionInterfaceParam(
                paymentInfo == null ? null : paymentInfo.getCollectionInterfaceParam());
        entity.setChannelCode(channelCode);
        if (channelParams instanceof Map<?, ?> params) {
            entity.setChannelParams((Map<String, Object>) params);
        }
        return handler.handleCol(entity);
    }

    private String resolveChannelCode(Object channelParams) {
        if (channelParams instanceof Map<?, ?> params) {
            Object value = params.get("channelCode");
            if (value != null) {
                return String.valueOf(value);
            }
        }
        return "digimone";
    }

    private String resolveCurrencyKey(String currency, String channelCode) {
        if ("alipay".equalsIgnoreCase(channelCode)) {
            return "ALIPAY";
        }
        return currency;
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

}
