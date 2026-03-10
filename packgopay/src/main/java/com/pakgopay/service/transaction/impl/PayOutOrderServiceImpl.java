package com.pakgopay.service.transaction.impl;

import com.pakgopay.common.config.RabbitConfig;
import com.pakgopay.common.constant.CommonConstant;
import com.pakgopay.common.enums.*;
import com.pakgopay.common.exception.PakGoPayException;
import com.pakgopay.data.entity.OrderQueryEntity;
import com.pakgopay.data.entity.TransactionInfo;
import com.pakgopay.data.entity.transaction.PayCreateEntity;
import com.pakgopay.data.entity.transaction.PayQueryEntity;
import com.pakgopay.data.reqeust.transaction.NotifyRequest;
import com.pakgopay.data.reqeust.transaction.OrderQueryRequest;
import com.pakgopay.data.reqeust.transaction.PayOutOrderRequest;
import com.pakgopay.data.reqeust.transaction.QueryOrderApiRequest;
import com.pakgopay.data.response.CommonResponse;
import com.pakgopay.data.response.PayOutOrderPageResponse;
import com.pakgopay.data.response.http.PaymentHttpResponse;
import com.pakgopay.mapper.PayOrderMapper;
import com.pakgopay.mapper.dto.MerchantInfoDto;
import com.pakgopay.mapper.dto.PayOrderDto;
import com.pakgopay.mapper.dto.PaymentDto;
import com.pakgopay.service.BalanceService;
import com.pakgopay.service.MerchantService;
import com.pakgopay.service.impl.ChannelPaymentServiceImpl;
import com.pakgopay.service.transaction.*;
import com.pakgopay.util.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
public class PayOutOrderServiceImpl extends BaseOrderService implements PayOutOrderService {

    @Autowired
    private MerchantCheckService merchantCheckService;

    @Autowired
    private MerchantService merchantService;

    @Autowired
    private ChannelPaymentServiceImpl channelPaymentService;

    @Autowired
    private BalanceService balanceService;

    @Autowired
    private PayOrderMapper payOrderMapper;

    @Autowired
    private TransactionUtil transactionUtil;

    @Autowired
    private SnowflakeIdService snowflakeIdService;

    @Override
    public CommonResponse createPayOutOrder(
            PayOutOrderRequest payOrderRequest, String authorization) throws PakGoPayException {
        MerchantInfoDto merchantInfoDto = validateApiKeyAndMerchant(payOrderRequest.getMerchantId(), authorization);
        validatePayOutSign(payOrderRequest, merchantInfoDto);
        return createPayOutOrderInternal(
                payOrderRequest,
                merchantInfoDto,
                "createPayOutOrder",
                CommonConstant.ORDER_TYPE_SYSTEM);
    }

    @Override
    public CommonResponse manualCreatePayOutOrder(
            PayOutOrderRequest payOrderRequest) throws PakGoPayException {
        MerchantInfoDto merchantInfoDto = merchantService.fetchMerchantInfo(payOrderRequest.getMerchantId());
        return createPayOutOrderInternal(
                payOrderRequest,
                merchantInfoDto,
                "manualCreatePayOutOrder",
                CommonConstant.ORDER_TYPE_MANUAL);
    }

    private CommonResponse createPayOutOrderInternal(
            PayOutOrderRequest payOrderRequest,
            MerchantInfoDto merchantInfoDto,
            String scene,
            Integer orderType) throws PakGoPayException {
        log.info("createPayOutOrder start, merchantId={}, merchantOrderNo={}, currency={}, amount={}, paymentNo={}, orderType={}",
                payOrderRequest.getMerchantId(),
                payOrderRequest.getMerchantOrderNo(),
                payOrderRequest.getCurrency(),
                payOrderRequest.getAmount(),
                payOrderRequest.getPaymentNo(),
                orderType);
        BigDecimal frozenAmount = BigDecimal.ZERO;
        boolean frozen = false;
        boolean lockAcquired = false;
        boolean createdSuccess = false;
        TransactionInfo transactionInfo = new TransactionInfo();
        try {
            // 1. load merchant info
            transactionInfo.setMerchantInfo(merchantInfoDto);
            log.info("merchant info loaded, userId={}, agentId={}, channelIds={}",
                    merchantInfoDto.getUserId(),
                    merchantInfoDto.getParentId(),
                    merchantInfoDto.getChannelIds());

            // 2. create-order idempotency lock
            acquireCreateOrderLock(
                    "payout",
                    payOrderRequest.getMerchantId(),
                    payOrderRequest.getMerchantOrderNo());
            lockAcquired = true;

            // 3. check request validate
            validatePayOutRequest(payOrderRequest, merchantInfoDto);
            transactionInfo.setRequestIp(payOrderRequest.getClientIp());
            log.info("payout request validated, merchantId={}", payOrderRequest.getMerchantId());

            // 4. get available payment id
            transactionInfo.setCurrency(payOrderRequest.getCurrency());
            transactionInfo.setAmount(payOrderRequest.getAmount());
            transactionInfo.setPaymentNo(payOrderRequest.getPaymentNo());

            channelPaymentService.selectPaymentId(CommonConstant.SUPPORT_TYPE_PAY, transactionInfo);
            log.info("payment resolved, paymentId={}, channelId={}",
                    transactionInfo.getPaymentId(),
                    transactionInfo.getChannelId());

            // 5. create system transaction no
            String systemTransactionNo = snowflakeIdService.nextId(CommonConstant.PAYOUT_PREFIX);
            log.info("generator system transactionNo :{}", systemTransactionNo);
            transactionInfo.setTransactionNo(systemTransactionNo);

            // 6. calculate transaction fee
            transactionInfo.setActualAmount(payOrderRequest.getAmount());
            channelPaymentService.calculateTransactionFees(transactionInfo, OrderType.PAY_OUT_ORDER);
            log.info("fee calculated, merchantFee={}, agent1Fee={}, agent2Fee={}, agent3Fee={}",
                    transactionInfo.getMerchantFee(),
                    transactionInfo.getAgent1Fee(),
                    transactionInfo.getAgent2Fee(),
                    transactionInfo.getAgent3Fee());

            // 7. build payout order dto
            PayOrderDto payOrderDto = buildPayOrderDto(payOrderRequest, transactionInfo, orderType);
            log.info("payOrderDto built, transactionNo={}, paymentId={}, channelId={}, paymentMode={}",
                    payOrderDto.getTransactionNo(),
                    payOrderDto.getPaymentId(),
                    payOrderDto.getChannelId(),
                    payOrderDto.getPaymentMode());

            // 8. freeze balance, persist processing order and publish timeout message
            frozenAmount = CalcUtil.safeAdd(transactionInfo.getMerchantFee(), payOrderRequest.getAmount());
            BigDecimal frozenAmountSnapshot = frozenAmount;
            transactionUtil.runInTransaction(() -> {
                CommonUtil.withBalanceLogContext("payout.create", transactionInfo.getTransactionNo(), () -> {
                    balanceService.freezeBalance(
                            frozenAmountSnapshot,
                            payOrderRequest.getMerchantId(),
                            payOrderRequest.getCurrency());
                });

                int ret = payOrderMapper.insert(payOrderDto);
                if (ret <= 0) {
                    throw new PakGoPayException(ResultCode.DATA_BASE_ERROR, "pay order insert failed");
                }
            });
            frozen = true;
            log.info("balance frozen, userId={}, currency={}, frozenAmount={}",
                    payOrderRequest.getMerchantId(), payOrderRequest.getCurrency(), frozenAmount);
            log.info("pay order inserted, transactionNo={}", payOrderDto.getTransactionNo());
            publishOrderTimeoutMessage(
                    RabbitConfig.ORDER_TIMEOUT_PAYING_QUEUE,
                    "payout",
                    payOrderDto.getTransactionNo(),
                    payOrderDto.getCreateTime());

            // 9. dispatch payout request to handler
            PaymentHttpResponse handlerResponse = dispatchPayOutOrder(
                    payOrderDto,
                    payOrderRequest,
                    transactionInfo.getPaymentInfo());
            log.info("payout handler dispatched, transactionNo={}, responseType={}",
                    payOrderDto.getTransactionNo(),
                    handlerResponse == null ? null : handlerResponse.getClass().getSimpleName());
            Integer handlerCode = extractCreateHandlerCode(handlerResponse);
            if (handlerCode != null && !Integer.valueOf(0).equals(handlerCode)) {
                markPayOrderFailedByDispatch(payOrderDto.getTransactionNo(),
                        "channel_request_failed_code_" + handlerCode);
                throw new PakGoPayException(ResultCode.HTTP_REQUEST_ERROR,
                        "payout channel request failed, code=" + handlerCode);
            }

            // 10. build and return create response
            Map<String, Object> responseBody = buildPayOutResponse(payOrderDto, handlerResponse);
            log.info("{} success, transactionNo={}", scene, payOrderDto.getTransactionNo());
            createdSuccess = true;
            return CommonResponse.success(responseBody);
        } catch (Exception e) {
            if (frozen) {
                try {
                    BigDecimal frozenAmountSnapshot = frozenAmount;
                    CommonUtil.withBalanceLogContext("payout.create", transactionInfo.getTransactionNo(), () -> {
                        balanceService.releaseFrozenBalance(
                                payOrderRequest.getMerchantId(),
                                payOrderRequest.getCurrency(),
                                frozenAmountSnapshot);
                    });
                } catch (Exception ex) {
                    log.error("releaseFrozenBalance failed, message {}", ex.getMessage());
                }
            }
            if (lockAcquired && !createdSuccess) {
                releaseCreateOrderLock(
                        "payout",
                        payOrderRequest.getMerchantId(),
                        payOrderRequest.getMerchantOrderNo());
            }
            log.error("{} failed, message {}", scene, e.getMessage());
            if (e instanceof PakGoPayException pe) {
                throw pe;
            }
            throw new PakGoPayException(ResultCode.FAIL, e.getMessage());
        }
    }

    @Override
    public CommonResponse queryOrderInfo(
            QueryOrderApiRequest queryRequest, String authorization) throws PakGoPayException {
        String userId = queryRequest.getMerchantId();
        String merchantOrderNo = queryRequest.getMerchantOrderNo();
        log.info("queryOrderInfo start, userId={}, merchantOrderNo={}", userId, merchantOrderNo);
        MerchantInfoDto merchantInfoDto = validateApiKeyAndMerchant(userId, authorization);
        validateQueryOrderSign(queryRequest, merchantInfoDto);
        // query payout order info  from db
        PayOrderDto payOrderDto;
        try {
            long[] range = resolveCurrentMonthTimeRange();
            payOrderDto = payOrderMapper.findByMerchantOrderNo(userId, merchantOrderNo, range[0], range[1])
                            .orElseThrow(() -> new PakGoPayException(ResultCode.MERCHANT_ORDER_NO_NOT_EXISTS));
        } catch (PakGoPayException e) {
            log.error("record is not exists, merchantOrderNo {}", merchantOrderNo);
            throw e;
        } catch (Exception e) {
            log.error("payOrderMapper findByMerchantOrderNo failed, message {}", e.getMessage());
            throw new PakGoPayException(ResultCode.DATA_BASE_ERROR);
        }

        // check if the requester and the order owner are the same.
        if (!payOrderDto.getMerchantUserId().equals(userId)) {
            log.error("he order does not belong to user, userId: {} merchantOrderNo: {}", userId, merchantOrderNo);
            throw new PakGoPayException(ResultCode.ORDER_PARAM_VALID, "the order does not belong to user");
        }

        // construct return data
        Map<String, Object> result = new HashMap<>();
        result.put("merchantOrderNo", payOrderDto.getMerchantOrderNo());
        result.put("transactionNo", payOrderDto.getTransactionNo());
        result.put("amount", payOrderDto.getAmount());
        result.put("currency", payOrderDto.getCurrencyType());
        result.put("status", payOrderDto.getOrderStatus());
        result.put("createTime", payOrderDto.getCreateTime().toString());
        result.put("updateTime", payOrderDto.getUpdateTime().toString());

        // TODO If the transaction fails, return the reason for the failure.
        if (OrderStatus.FAILED.getCode().toString().equals(payOrderDto.getOrderStatus())) {
            result.put("failureReason", "");
        }

        if (OrderStatus.SUCCESS.getCode().toString().equals(payOrderDto.getOrderStatus())) {
            Long successCallBackTime = payOrderDto.getSuccessCallbackTime();
            if (successCallBackTime != null) {
                result.put("successCallBackTime", successCallBackTime.toString());
            } else {
                result.put("successCallBackTime", null);
                log.warn(
                        "The transaction status is successful, but the payment time is empty. transaction number: {}",
                        payOrderDto.getMerchantOrderNo());
            }
        }

        return CommonResponse.success(result);
    }

    @Override
    public CommonResponse queryPayOutOrders(OrderQueryRequest request) throws PakGoPayException {
        log.info("queryPayOutOrders start, merchantUserId={}, transactionNo={}, merchantOrderNo={}",
                request.getMerchantUserId(), request.getTransactionNo(), request.getMerchantOrderNo());
        OrderQueryEntity entity = buildOrderQueryEntity(request);

        PayOutOrderPageResponse response = new PayOutOrderPageResponse();
        try {
            Integer totalNumber = payOrderMapper.countByQuery(entity);
            response.setTotalNumber(totalNumber);
            response.setPayOrderDtoList(payOrderMapper.pageByQuery(entity));
        } catch (Exception e) {
            log.error("queryPayOutOrders failed, message {}", e.getMessage());
            throw new PakGoPayException(ResultCode.DATA_BASE_ERROR);
        }
        response.setPageNo(entity.getPageNo());
        response.setPageSize(entity.getPageSize());
        log.info("queryPayOutOrders end, totalNumber={}", response.getTotalNumber());
        return CommonResponse.success(response);
    }

    @Override
    public Object handleNotify(Map<String, Object> notifyData) throws PakGoPayException {
        log.info("handleNotify start, notifyData: {}", notifyData);
        // Verify order existence and validate state transition.
        PayOrderDto payOrderDto = validateNotifyOrder(notifyData);
        log.info("notify order validated, transactionNo={}, orderStatus={}",
                payOrderDto.getTransactionNo(), payOrderDto.getOrderStatus());

        OrderScope scope = Integer.valueOf(1).equals(payOrderDto.getPaymentMode())
                ? OrderScope.THIRD_PARTY
                : OrderScope.SYSTEM;
        OrderHandler handler = OrderHandlerFactory.get(
                scope, payOrderDto.getCurrencyType(), payOrderDto.getPaymentNo());
        // Query payment config early for subsequent channel query.
        PaymentDto paymentDto = fetchPaymentById(payOrderDto.getPaymentId());

        NotifyRequest response = null;
        TransactionStatus targetStatus = TransactionStatus.FAILED;
        try {
            // Parse notify payload into a structured response.
            response = handler.handleNotify(
                    notifyData,
                    paymentDto == null ? null : paymentDto.getPayInterfaceParam());
            log.info("notify parsed, transactionNo={}, merchantNo={}, status={}",
                    response.getTransactionNo(), response.getMerchantNo(), response.getStatus());

            // Validate required fields in the notify response.
            OrderHandler.validateNotifyResponse(response);
            log.info("notify response validated, transactionNo={}", response.getTransactionNo());

            targetStatus = resolveTargetStatus(
                    payOrderDto,
                    response.getStatus(),
                    paymentDto,
                    handler,
                    "notify");
        } catch (Exception e) {
            log.warn("handleNotify pre-check failed, transactionNo={}, message={}",
                    payOrderDto.getTransactionNo(), e.getMessage());
            targetStatus = TransactionStatus.FAILED;
        }

        processNotifyResult(payOrderDto, targetStatus, handler, NotifyFlow.AUTO);

        return handler.getNotifySuccessResponse();
    }

    /**
     * Manual notify entry: reuse notify processing flow without third-party sign/map parsing.
     */
    @Override
    public CommonResponse manualHandleNotify(NotifyRequest notifyRequest) throws PakGoPayException {
        log.info("manualHandleNotify start, notifyRequest={}", notifyRequest);
        // Validate normalized notify payload and ensure order is still mutable.
        OrderHandler.validateNotifyResponse(notifyRequest);
        PayOrderDto payOrderDto = validateNotifyOrder(notifyRequest.getTransactionNo());
        log.info("manual notify order validated, transactionNo={}, orderStatus={}",
                payOrderDto.getTransactionNo(), payOrderDto.getOrderStatus());

        OrderScope scope = Integer.valueOf(1).equals(payOrderDto.getPaymentMode())
                ? OrderScope.THIRD_PARTY
                : OrderScope.SYSTEM;
        OrderHandler handler = OrderHandlerFactory.get(
                scope, payOrderDto.getCurrencyType(), payOrderDto.getPaymentNo());
        // Manual notify uses request status code directly without upstream status query.
        TransactionStatus targetStatus;
        try {
            targetStatus = TransactionStatus.fromCode(notifyRequest.getStatus());
        } catch (IllegalArgumentException e) {
            throw new PakGoPayException(ResultCode.ORDER_PARAM_VALID, e.getMessage());
        }

        // Execute shared post-notify pipeline.
        processNotifyResult(payOrderDto, targetStatus, handler, NotifyFlow.MANUAL);
        log.info("manualHandleNotify end, transactionNo={}, targetStatus={}",
                payOrderDto.getTransactionNo(), targetStatus);
        return CommonResponse.success(ResultCode.SUCCESS);
    }

    /**
     * Resolve final target status from notify status and active channel query.
     */
    private TransactionStatus resolveTargetStatus(
            PayOrderDto payOrderDto,
            String notifyStatusText,
            PaymentDto paymentDto,
            OrderHandler handler,
            String scene) {
        // Convert upstream notify status text to internal enum first.
        TransactionStatus notifyStatus = resolveNotifyStatus(notifyStatusText);
        if (TransactionStatus.FAILED.equals(notifyStatus)) {
            log.warn("{} status is FAILED, transactionNo={}", scene, payOrderDto.getTransactionNo());
            return TransactionStatus.FAILED;
        }
        log.info("{} status is not FAILED, transactionNo={}, notifyStatus={}",
                scene, payOrderDto.getTransactionNo(), notifyStatusText);
        // For non-failed notify, query channel to get authoritative final status.
        return queryOrderTargetStatus(payOrderDto, paymentDto, handler);
    }

    /**
     * Execute shared post-notify pipeline: update order, callback merchant, refresh report.
     */
    private void processNotifyResult(
            PayOrderDto payOrderDto,
            TransactionStatus targetStatus,
            OrderHandler handler,
            NotifyFlow notifyFlow) throws PakGoPayException {
        String scene = notifyFlow.getScene();
        // Persist order status transition and related balance changes.
        boolean updated = applyNotifyUpdate(
                payOrderDto, targetStatus, notifyFlow.isAllowFailedToSuccess(), notifyFlow);
        boolean continuePostProcess = updated
                || (TransactionStatus.SUCCESS.equals(targetStatus)
                && TransactionStatus.SUCCESS.getCode().toString().equals(payOrderDto.getOrderStatus()));
        log.info("{} status transition handled, transactionNo={}, currentStatus={}, targetStatus={}, updated={}, continuePostProcess={}",
                scene,
                payOrderDto.getTransactionNo(),
                payOrderDto.getOrderStatus(),
                targetStatus,
                updated,
                continuePostProcess);
        if (!continuePostProcess) {
            return;
        }

        // Callback result only affects callback metadata, not order final status.
        // Execute callback side-effects asynchronously to avoid blocking API response.
        if (payOrderDto.getCallbackUrl() != null && !payOrderDto.getCallbackUrl().isBlank()) {
            runCallbackAsync(scene, payOrderDto.getTransactionNo(), () -> {
                try {
                    OrderHandler.NotifyResult notifyResult = handler.sendNotifyToMerchant(
                            buildPayNotifyBody(payOrderDto, targetStatus),
                            payOrderDto.getCallbackUrl());
                    updateNotifyCallbackMeta(payOrderDto, notifyResult);
                } catch (Exception e) {
                    log.error("{} merchant callback execution failed, transactionNo={}, message={}",
                            scene, payOrderDto.getTransactionNo(), e.getMessage());
                }
            });
        } else {
            log.info("{} skip merchant callback, callbackUrl empty, transactionNo={}",
                    scene, payOrderDto.getTransactionNo());
        }
        // Refresh report only when DB status actually changed.
        if (updated) {
            refreshReportData(payOrderDto.getCreateTime(), payOrderDto.getCurrencyType());
            log.info("{} report refreshed, transactionNo={}, currency={}",
                    scene, payOrderDto.getTransactionNo(), payOrderDto.getCurrencyType());
        } else {
            log.info("{} skip report refresh, transactionNo={}, updated={}",
                    scene, payOrderDto.getTransactionNo(), updated);
        }
    }

    private void updateNotifyCallbackMeta(PayOrderDto orderDto, OrderHandler.NotifyResult notifyResult) {
        long now = System.currentTimeMillis() / 1000;
        boolean success = notifyResult != null && notifyResult.isSuccess();
        int callbackStatus = success ? 2 : 1;
        int failedAttempts = notifyResult == null ? 1 : Math.max(notifyResult.getFailedAttempts(), 0);
        int totalAttempts = failedAttempts + (success ? 1 : 0);
        try {
            long[] range = resolveTransactionNoTimeRange(orderDto.getTransactionNo());
            payOrderMapper.increaseCallbackTimes(
                    orderDto.getTransactionNo(),
                    now,
                    totalAttempts,
                    callbackStatus,
                    success ? now : null,
                    range[0],
                    range[1]);
            log.info("merchant notify callback meta updated, transactionNo={}, totalAttempts={}, success={}, callbackStatus={}",
                    orderDto.getTransactionNo(), totalAttempts, success, callbackStatus);
        } catch (Exception e) {
            log.error("update notify callback meta failed, transactionNo={}, message={}",
                    orderDto.getTransactionNo(), e.getMessage());
        }
    }

    /**
     * Validate sign for payout create API request.
     */
    private void validatePayOutSign(PayOutOrderRequest request, MerchantInfoDto merchantInfoDto) {
        Map<String, Object> payload = buildSignPayload(request,
                "merchantId", "merchantOrderNo", "paymentNo", "amount", "currency",
                "notificationUrl", "bankCode", "accountName", "accountNo",
                "channelParams", "remark");
        log.info("validatePayOutSign, merchantId={}, merchantOrderNo={}",
                request.getMerchantId(), request.getMerchantOrderNo());
        validateRequestSign(payload, request.getSign(), merchantInfoDto, "payout");
    }

    /**
     * Validate sign for query order API request.
     */
    private void validateQueryOrderSign(QueryOrderApiRequest request, MerchantInfoDto merchantInfoDto) {
        Map<String, Object> payload = buildSignPayload(request,
                "merchantId", "merchantOrderNo", "orderType");
        log.info("validateQueryOrderSign, merchantId={}, merchantOrderNo={}",
                request.getMerchantId(), request.getMerchantOrderNo());
        validateRequestSign(payload, request.getSign(), merchantInfoDto, "queryOrder");
    }

    private void validatePayOutRequest(
            PayOutOrderRequest payOutOrderRequest, MerchantInfoDto merchantInfoDto) throws PakGoPayException {
        log.info("validatePayOutRequest start");

        // check merchant is support payout
        if (merchantInfoDto.getSupportType() != 1 && merchantInfoDto.getSupportType() != 2) {
            log.error("The merchant is not support payout, merchantName: {}", merchantInfoDto.getMerchantName());
            throw new PakGoPayException(ResultCode.MERCHANT_NOT_SUPPORT_PAYOUT);
        }

        // check ip white list
        if (!merchantCheckService.isPayIpAllowed(payOutOrderRequest.getClientIp(), merchantInfoDto.getPayWhiteIps())) {
            log.error("isPayIpAllowed failed, clientIp: {}", payOutOrderRequest.getClientIp());
            throw new PakGoPayException(ResultCode.IS_NOT_WHITE_IP);
        }

        // check Merchant order code is uniqueness
        if (!merchantCheckService.existsPayMerchantOrderNo(
                payOutOrderRequest.getMerchantId(),
                payOutOrderRequest.getMerchantOrderNo())) {
            log.error("existsColMerchantOrderNo failed, merchantOrderNo: {}", payOutOrderRequest.getMerchantOrderNo());
            throw new PakGoPayException(ResultCode.MERCHANT_CODE_IS_EXISTS);
        }

        // amount check
        if (payOutOrderRequest.getAmount().compareTo(BigDecimal.ZERO) <= CommonConstant.ZERO) {
            log.error("The transaction amount must be greater than 0, amount: {}", payOutOrderRequest.getAmount());
            throw new PakGoPayException(ResultCode.ORDER_PARAM_VALID, "The transaction amount must be greater than 0.");
        }

        // check user is enabled
        if (!merchantCheckService.isEnableMerchant(merchantInfoDto)) {
            log.error("The merchant status is disable, merchantName: {}", merchantInfoDto.getMerchantName());
            throw new PakGoPayException(ResultCode.USER_NOT_ENABLE);
        }
        log.info("validatePayoutRequest success");
    }

    private PayOrderDto validateNotifyOrder(Map<String, Object> notifyData) throws PakGoPayException {
        String transactionNo = extractTransactionNo(notifyData);
        return validateNotifyOrder(transactionNo);
    }

    private PayOrderDto validateNotifyOrder(String transactionNo) throws PakGoPayException {
        log.info("validateNotifyOrder start, transactionNo={}", transactionNo);
        long[] range = resolveTransactionNoTimeRange(transactionNo);
        PayOrderDto payOrderDto = payOrderMapper.findByTransactionNo(
                        transactionNo,
                        range[0],
                        range[1])
                .orElseThrow(() -> new PakGoPayException(ResultCode.MERCHANT_ORDER_NO_NOT_EXISTS,
                        "record is not exists, transactionNo:" + transactionNo));
        log.info("validateNotifyOrder success, transactionNo={}, orderStatus={}",
                payOrderDto.getTransactionNo(), payOrderDto.getOrderStatus());
        return payOrderDto;
    }

    private boolean applyNotifyUpdate(
            PayOrderDto payOrderDto,
            TransactionStatus targetStatus,
            boolean allowFailedToSuccess,
            NotifyFlow notifyFlow) throws PakGoPayException {
        String currentStatus = payOrderDto.getOrderStatus();
        log.info("payout applyNotifyUpdate, transactionNo={}, currentStatus={}, targetStatus={}, allowFailedToSuccess={}",
                payOrderDto.getTransactionNo(), currentStatus, targetStatus, allowFailedToSuccess);
        if (TransactionStatus.SUCCESS.getCode().toString().equals(currentStatus)) {
            log.info("payout applyNotifyUpdate skip: already SUCCESS, transactionNo={}",
                    payOrderDto.getTransactionNo());
            return false;
        }
        if (TransactionStatus.FAILED.getCode().toString().equals(currentStatus)
                && !(allowFailedToSuccess && TransactionStatus.SUCCESS.equals(targetStatus))) {
            log.info("payout applyNotifyUpdate skip: current status is FAILED and migration not allowed, transactionNo={}",
                    payOrderDto.getTransactionNo());
            return false;
        }
        if (TransactionStatus.FAILED.getCode().toString().equals(currentStatus)
                && allowFailedToSuccess && TransactionStatus.SUCCESS.equals(targetStatus)) {
            log.info("payout applyNotifyUpdate allow migration: FAILED -> SUCCESS, transactionNo={}",
                    payOrderDto.getTransactionNo());
        }
        MerchantInfoDto merchantInfo = merchantService.fetchMerchantInfo(payOrderDto.getMerchantUserId());
        if (merchantInfo == null) {
            throw new PakGoPayException(ResultCode.USER_IS_NOT_EXIST);
        }

        PayOrderDto update = new PayOrderDto();
        update.setTransactionNo(payOrderDto.getTransactionNo());
        update.setOrderStatus(targetStatus.getCode().toString());
        update.setOperateType(notifyFlow.getOperateType());
        if (TransactionStatus.SUCCESS.equals(targetStatus)) {
            update.setSuccessCallbackTime(System.currentTimeMillis() / 1000);
        }
        if (TransactionStatus.FAILED.equals(targetStatus)) {
            update.setRemark("notify_failed");
        }
        update.setUpdateTime(System.currentTimeMillis() / 1000);
        long[] range = resolveTransactionNoTimeRange(payOrderDto.getTransactionNo());

        AtomicBoolean stateUpdated = new AtomicBoolean(false);
        transactionUtil.runInTransaction(() -> {
            try {
                int updated = payOrderMapper.updateByTransactionNoWhenProcessing(
                        update, currentStatus,
                        range[0], range[1]);
                log.info("payout applyNotifyUpdate db update result, transactionNo={}, updatedRows={}, expectedCurrentStatus={}, targetStatus={}",
                        payOrderDto.getTransactionNo(), updated, currentStatus, targetStatus.getCode());
                if (updated <= 0) {
                    log.info("payout notify skipped, order not processing, transactionNo={}, status={}",
                            payOrderDto.getTransactionNo(), targetStatus.getCode());
                    return;
                }
                stateUpdated.set(true);
                log.info("pay order updated, transactionNo={}, status={}",
                        payOrderDto.getTransactionNo(), targetStatus.getCode());
            } catch (Exception e) {
                log.error("pay order updateByTransactionNo failed, message {}", e.getMessage());
                throw new PakGoPayException(ResultCode.DATA_BASE_ERROR);
            }

            BigDecimal payoutAmount = resolveOrderAmount(payOrderDto.getActualAmount(), payOrderDto.getAmount());
            BigDecimal merchantFee = payOrderDto.getMerchantFee() == null ? BigDecimal.ZERO : payOrderDto.getMerchantFee();
            BigDecimal frozenAmount = CalcUtil.safeAdd(payoutAmount, merchantFee);

            if (TransactionStatus.SUCCESS.equals(targetStatus)) {
                CommonUtil.withBalanceLogContext("payout.handleNotify", payOrderDto.getTransactionNo(), () -> {
                    balanceService.confirmPayoutBalance(
                            payOrderDto.getMerchantUserId(),
                            payOrderDto.getCurrencyType(),
                            frozenAmount);
                });
                log.info("balance payout confirmed, transactionNo={}, frozenAmount={}",
                        payOrderDto.getTransactionNo(), frozenAmount);
                updateAgentFeeBalance(balanceService, merchantInfo, payOrderDto.getCurrencyType(),
                        payOrderDto.getAgent1Fee(),
                        payOrderDto.getAgent2Fee(),
                        payOrderDto.getAgent3Fee());
            }
            if (TransactionStatus.FAILED.equals(targetStatus)) {
                CommonUtil.withBalanceLogContext("payout.handleNotify", payOrderDto.getTransactionNo(), () -> {
                    balanceService.releaseFrozenBalance(
                            payOrderDto.getMerchantUserId(),
                            payOrderDto.getCurrencyType(),
                            frozenAmount);
                });
                log.info("balance payout released, transactionNo={}, frozenAmount={}",
                        payOrderDto.getTransactionNo(), frozenAmount);
            }
        });
        log.info("payout applyNotifyUpdate end, transactionNo={}, stateUpdated={}",
                payOrderDto.getTransactionNo(), stateUpdated.get());
        return stateUpdated.get();
    }

    private TransactionStatus queryOrderTargetStatus(
            PayOrderDto payOrderDto, PaymentDto paymentDto, OrderHandler handler) {
        log.info("queryOrderTargetStatus start, transactionNo={}",
                payOrderDto == null ? null : payOrderDto.getTransactionNo());
        if (handler == null || payOrderDto == null) {
            log.warn("queryOrderTargetStatus fallback FAILED, reason=handler_or_order_null, transactionNo={}",
                    payOrderDto == null ? null : payOrderDto.getTransactionNo());
            return TransactionStatus.FAILED;
        }
        try {
            PayQueryEntity query = new PayQueryEntity();
            query.setTransactionNo(payOrderDto.getTransactionNo());
            query.setPaymentCheckPayUrl(paymentDto.getPaymentCheckPayUrl());
            query.setPayInterfaceParam(paymentDto.getPayInterfaceParam());
            TransactionStatus queryStatus = handler.handlePayQuery(query);
            log.info("queryOrderTargetStatus result, transactionNo={}, queryStatus={}",
                    payOrderDto.getTransactionNo(), queryStatus);
            return queryStatus;
        } catch (PakGoPayException e) {
            throw e;
        } catch (Exception e) {
            log.warn("queryOrderTargetStatus failed, transactionNo={}, message={}",
                    payOrderDto.getTransactionNo(), e.getMessage());
            return TransactionStatus.FAILED;
        }
    }

    private PaymentHttpResponse dispatchPayOutOrder(
            PayOrderDto dto, PayOutOrderRequest request, PaymentDto paymentInfo) {
        OrderScope scope = Integer.valueOf(1).equals(dto.getPaymentMode())
                ? OrderScope.THIRD_PARTY
                : OrderScope.SYSTEM;
        OrderHandler handler = OrderHandlerFactory.get(
                scope, dto.getCurrencyType(), dto.getPaymentNo());
        PayCreateEntity payRequest = new PayCreateEntity();
        payRequest.setTransactionNo(dto.getTransactionNo());
        payRequest.setAmount(dto.getAmount());
        payRequest.setCurrency(dto.getCurrencyType());
        payRequest.setMerchantOrderNo(dto.getTransactionNo());
        payRequest.setMerchantUserId(dto.getMerchantUserId());
        payRequest.setCallbackUrl(paymentInfo == null ? null : paymentInfo.getPayCallbackAddr());
        payRequest.setBankCode(request.getBankCode());
        payRequest.setAccountName(request.getAccountName());
        payRequest.setAccountNo(request.getAccountNo());
        if (request.getChannelParams() instanceof Map<?, ?> params) {
            payRequest.setChannelParams((Map<String, Object>) params);
        }
        payRequest.setIp(dto.getRequestIp());
        payRequest.setPaymentRequestPayUrl(
                paymentInfo == null ? null : paymentInfo.getPaymentRequestPayUrl());
        payRequest.setPayInterfaceParam(
                paymentInfo == null ? null : paymentInfo.getPayInterfaceParam());
        log.info("dispatchPayOutOrder payload ready, transactionNo={}, paymentMode={}, currency={}",
                dto.getTransactionNo(), dto.getPaymentMode(), dto.getCurrencyType());
        return handler.handlePay(payRequest);
    }

    private Map<String, Object> buildPayOutResponse(PayOrderDto dto, PaymentHttpResponse handlerResponse) {
        Map<String, Object> result = new HashMap<>();
        result.put("amount", dto.getAmount());
        result.put("transactionNo", dto.getTransactionNo());
        result.put("merchantOrderNo", dto.getMerchantOrderNo());
        result.put("currency", dto.getCurrencyType());
        result.put("createTime", dto.getCreateTime().toString());
        result.put("status", dto.getOrderStatus());

        Map<String, Object> handlerData = extractHandlerData(handlerResponse);
        if (handlerData != null) {
            mergeIfPresent(result, handlerData, "status");
            mergeIfPresent(result, handlerData, "payUrl");
            mergeIfPresent(result, handlerData, "cashierUrl");
            mergeIfPresent(result, handlerData, "qrCode");
        }
        return result;
    }

    private void markPayOrderFailedByDispatch(String transactionNo, String remark) {
        try {
            long now = System.currentTimeMillis() / 1000;
            long[] range = resolveTransactionNoTimeRange(transactionNo);
            PayOrderDto update = new PayOrderDto();
            update.setTransactionNo(transactionNo);
            update.setOrderStatus(String.valueOf(TransactionStatus.FAILED.getCode()));
            update.setUpdateTime(now);
            update.setRemark(remark);
            int updated = payOrderMapper.updateByTransactionNoWhenProcessing(
                    update,
                    String.valueOf(TransactionStatus.PROCESSING.getCode()),
                    range[0],
                    range[1]);
            log.info("markPayOrderFailedByDispatch done, transactionNo={}, updated={}, remark={}",
                    transactionNo, updated, remark);
        } catch (Exception e) {
            log.error("markPayOrderFailedByDispatch failed, transactionNo={}, message={}",
                    transactionNo, e.getMessage());
        }
    }

    private PayOrderDto buildPayOrderDto(
            PayOutOrderRequest request,
            TransactionInfo transactionInfo,
            Integer orderType) {
        long now = resolveCreateTimeFromTransactionNo(transactionInfo.getTransactionNo());
        MerchantInfoDto merchantInfo = transactionInfo.getMerchantInfo();

        // -----------------------------
        // 1) Core identifiers & amounts
        // -----------------------------
        PayOrderDto dto = new PayOrderDto();
        PatchBuilderUtil<PayOutOrderRequest, PayOrderDto> builder = PatchBuilderUtil.from(request).to(dto);
        builder.obj(transactionInfo::getTransactionNo, dto::setTransactionNo) // system transaction no
                .obj(request::getMerchantOrderNo, dto::setMerchantOrderNo) // merchant order no
                .obj(request::getAmount, dto::setAmount) // requested amount
                .obj(request::getCurrency, dto::setCurrencyType) // currency code
                .obj(request::getAmount, dto::setActualAmount)// actualAmount

        // -----------------------------
        // 2) Merchant ownership linkage
        // -----------------------------
        .obj(() -> merchantInfo != null ? merchantInfo.getUserId() : request.getMerchantId(),
                dto::setMerchantUserId) // merchant user id

        // -----------------------------
        // 3) Channel/payment association
        // -----------------------------
        .obj(transactionInfo::getPaymentId, dto::setPaymentId) // payment channel id
        .obj(transactionInfo::getPaymentNo, dto::setPaymentNo) // payment channel no
                .obj(transactionInfo::getChannelId, dto::setChannelId); // channel id

        // -----------------------------
        // 4) Merchant & agent fee config
        // -----------------------------
        if (transactionInfo.getPaymentInfo() != null) {
            int paymentMode = "1".equals(transactionInfo.getPaymentInfo().getIsThird()) ? 2 : 1;
            builder.obj(() -> paymentMode, dto::setPaymentMode); // payment mode
        }
        builder.obj(transactionInfo::getMerchantRate, dto::setMerchantRate) // merchant pay rate
        .obj(transactionInfo::getMerchantFixedFee, dto::setMerchantFixedFee) // merchant fixed fee
        .obj(transactionInfo::getMerchantFee, dto::setMerchantFee) // merchant fee
        .obj(transactionInfo::getAgent1Rate, dto::setAgent1Rate) // agent1 rate
        .obj(transactionInfo::getAgent1FixedFee, dto::setAgent1FixedFee) // agent1 fixed fee
        .obj(transactionInfo::getAgent1Fee, dto::setAgent1Fee) // agent1 fee
        .obj(transactionInfo::getAgent2Rate, dto::setAgent2Rate) // agent2 rate
        .obj(transactionInfo::getAgent2FixedFee, dto::setAgent2FixedFee) // agent2 fixed fee
        .obj(transactionInfo::getAgent2Fee, dto::setAgent2Fee) // agent2 fee
        .obj(transactionInfo::getAgent3Rate, dto::setAgent3Rate) // agent3 rate
        .obj(transactionInfo::getAgent3FixedFee, dto::setAgent3FixedFee) // agent3 fixed fee
        .obj(transactionInfo::getAgent3Fee, dto::setAgent3Fee) // agent3 fee

        // -----------------------------
        // 5) Callback & request metadata
        // -----------------------------
        .obj(() -> orderType, dto::setOrderType) // order type: 1-system, 2-manual, 3-test
        .obj(() -> OrderStatus.PROCESSING.getCode().toString(), dto::setOrderStatus) // order status: 1-processing, 2-failed
        .obj(() -> 0, dto::setCallbackStatus) // callback status: 0-pending, 1-failed, 2-success
        .obj(request::getNotificationUrl, dto::setCallbackUrl) // async callback url
        .obj(() -> CommonConstant.ZERO, dto::setCallbackTimes) // initial callback times
        .obj(request::getClientIp, dto::setRequestIp) // request ip
        .obj(request::getRemark, dto::setRemark) // remark
        .obj(() -> now, dto::setRequestTime) // request time
        .obj(() -> now, dto::setCreateTime) // create time
        .obj(() -> now, dto::setUpdateTime); // update time

        // Unassigned fields:

        // lastCallbackTime
        // callbackStatus
        // successCallbackTime
        // operateType
        log.info("buildPayOrderDto finish, transactionNo={}, merchantUserId={}, paymentId={}, channelId={}",
                dto.getTransactionNo(), dto.getMerchantUserId(), dto.getPaymentId(), dto.getChannelId());
        return builder.build();
    }

}
