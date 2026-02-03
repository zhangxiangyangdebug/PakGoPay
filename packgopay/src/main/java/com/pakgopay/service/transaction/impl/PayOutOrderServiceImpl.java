package com.pakgopay.service.transaction.impl;

import com.pakgopay.common.constant.CommonConstant;
import com.pakgopay.data.entity.OrderQueryEntity;
import com.pakgopay.data.entity.TransactionInfo;
import com.pakgopay.common.enums.OrderScope;
import com.pakgopay.common.enums.OrderStatus;
import com.pakgopay.common.enums.OrderType;
import com.pakgopay.common.enums.ResultCode;
import com.pakgopay.common.enums.TransactionStatus;
import com.pakgopay.common.exception.PakGoPayException;
import com.pakgopay.data.reqeust.transaction.OrderQueryRequest;
import com.pakgopay.data.reqeust.transaction.PayOutOrderRequest;
import com.pakgopay.data.reqeust.transaction.NotifyRequest;
import com.pakgopay.data.response.CommonResponse;
import com.pakgopay.data.response.PayOutOrderPageResponse;
import com.pakgopay.mapper.PayOrderMapper;
import com.pakgopay.mapper.dto.MerchantInfoDto;
import com.pakgopay.mapper.dto.PayOrderDto;
import com.pakgopay.service.BalanceService;
import com.pakgopay.service.MerchantService;
import com.pakgopay.service.impl.ChannelPaymentServiceImpl;
import com.pakgopay.service.transaction.*;
import com.pakgopay.util.CommonUtil;
import com.pakgopay.util.PatchBuilderUtil;
import com.pakgopay.util.SnowflakeIdGenerator;
import com.pakgopay.util.TransactionUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

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

    @Override
    public CommonResponse createPayOutOrder(PayOutOrderRequest payOrderRequest) throws PakGoPayException {
        log.info("createPayOutOrder start, merchantId={}, merchantOrderNo={}, currency={}, amount={}, paymentNo={}",
                payOrderRequest.getMerchantId(),
                payOrderRequest.getMerchantOrderNo(),
                payOrderRequest.getCurrency(),
                payOrderRequest.getAmount(),
                payOrderRequest.getPaymentNo());
        BigDecimal frozenAmount = BigDecimal.ZERO;
        boolean frozen = false;
        TransactionInfo transactionInfo = new TransactionInfo();
        try {
            // 1. get merchant info
            MerchantInfoDto merchantInfoDto = merchantService.fetchMerchantInfo(payOrderRequest.getUserId());
            transactionInfo.setMerchantInfo(merchantInfoDto);
            // merchant is not exists
            if (merchantInfoDto == null) {
                log.error("merchant info is not exist, userId {}", payOrderRequest.getUserId());
                throw new PakGoPayException(ResultCode.USER_IS_NOT_EXIST);
            }
            log.info("merchant info loaded, userId={}, agentId={}, channelIds={}",
                    merchantInfoDto.getUserId(),
                    merchantInfoDto.getParentId(),
                    merchantInfoDto.getChannelIds());

            // 2. check request validate
            validatePayOutRequest(payOrderRequest, merchantInfoDto);
            transactionInfo.setRequestIp(payOrderRequest.getClientIp());
            log.info("payout request validated, merchantId={}", payOrderRequest.getMerchantId());

            // 3. get available payment id
            transactionInfo.setCurrency(payOrderRequest.getCurrency());
            transactionInfo.setAmount(payOrderRequest.getAmount());
            transactionInfo.setPaymentNo(payOrderRequest.getPaymentNo());

            channelPaymentService.selectPaymentId(CommonConstant.SUPPORT_TYPE_PAY, transactionInfo);
            log.info("payment resolved, paymentId={}, channelId={}",
                    transactionInfo.getPaymentId(),
                    transactionInfo.getChannelId());

            // 4. create system transaction no
            String systemTransactionNo = SnowflakeIdGenerator.getSnowFlakeId(CommonConstant.PAYOUT_PREFIX);
            log.info("generator system transactionNo :{}", systemTransactionNo);
            transactionInfo.setTransactionNo(systemTransactionNo);

            // 5. calculate transaction fee
            transactionInfo.setActualAmount(payOrderRequest.getAmount());
            channelPaymentService.calculateTransactionFees(transactionInfo, OrderType.PAY_OUT_ORDER);
            log.info("fee calculated, merchantFee={}, agent1Fee={}, agent2Fee={}, agent3Fee={}",
                    transactionInfo.getMerchantFee(),
                    transactionInfo.getAgent1Fee(),
                    transactionInfo.getAgent2Fee(),
                    transactionInfo.getAgent3Fee());

            PayOrderDto payOrderDto = buildPayOrderDto(payOrderRequest, transactionInfo);
            log.info("payOrderDto built, transactionNo={}, paymentId={}, channelId={}, paymentMode={}",
                    payOrderDto.getTransactionNo(),
                    payOrderDto.getPaymentId(),
                    payOrderDto.getChannelId(),
                    payOrderDto.getPaymentMode());

            frozenAmount = CommonUtil.safeAdd(transactionInfo.getMerchantFee(), payOrderRequest.getAmount());
            BigDecimal frozenAmountSnapshot = frozenAmount;
            transactionUtil.runInTransaction(() -> {
                CommonUtil.withBalanceLogContext("payout.create", transactionInfo.getTransactionNo(), () -> {
                    balanceService.freezeBalance(
                            frozenAmountSnapshot,
                            payOrderRequest.getUserId(),
                            payOrderRequest.getCurrency());
                });

                int ret = payOrderMapper.insert(payOrderDto);
                if (ret <= 0) {
                    throw new PakGoPayException(ResultCode.DATA_BASE_ERROR, "pay order insert failed");
                }
            });
            frozen = true;
            log.info("balance frozen, userId={}, currency={}, frozenAmount={}",
                    payOrderRequest.getUserId(), payOrderRequest.getCurrency(), frozenAmount);
            log.info("pay order inserted, transactionNo={}", payOrderDto.getTransactionNo());

            Object handlerResponse = dispatchPayOutOrder(payOrderDto, payOrderRequest);
            log.info("payout handler dispatched, transactionNo={}, responseType={}",
                    payOrderDto.getTransactionNo(),
                    handlerResponse == null ? null : handlerResponse.getClass().getSimpleName());

            Map<String, Object> responseBody = buildPayOutResponse(payOrderDto, handlerResponse);
            log.info("createPayOutOrder success, transactionNo={}", payOrderDto.getTransactionNo());
            return CommonResponse.success(responseBody);
        } catch (Exception e) {
            if (frozen) {
                try {
                    BigDecimal frozenAmountSnapshot = frozenAmount;
                    CommonUtil.withBalanceLogContext("payout.create", transactionInfo.getTransactionNo(), () -> {
                        balanceService.releaseFrozenBalance(
                                payOrderRequest.getUserId(),
                                payOrderRequest.getCurrency(),
                                frozenAmountSnapshot);
                    });
                } catch (Exception ex) {
                    log.error("releaseFrozenBalance failed, message {}", ex.getMessage());
                }
            }
            log.error("createPayOutOrder failed, message {}", e.getMessage());
            if (e instanceof PakGoPayException pe) {
                throw pe;
            }
            throw new PakGoPayException(ResultCode.FAIL, e.getMessage());
        }
    }

    @Override
    public CommonResponse queryOrderInfo(String userId, String transactionNo) throws PakGoPayException {
        log.info("queryOrderInfo start, userId={}, transactionNo={}", userId, transactionNo);
        // query payout order info  from db
        PayOrderDto payOrderDto;
        try {
            payOrderDto = payOrderMapper.findByTransactionNo(transactionNo)
                            .orElseThrow(() -> new PakGoPayException(ResultCode.MERCHANT_ORDER_NO_NOT_EXISTS));
        } catch (PakGoPayException e) {
            log.error("record is not exists, transactionNo {}", transactionNo);
            throw e;
        } catch (Exception e) {
            log.error("payOrderMapper findByTransactionNo failed, message {}", e.getMessage());
            throw new PakGoPayException(ResultCode.DATA_BASE_ERROR);
        }

        // check if the requester and the order owner are the same.
        if (!payOrderDto.getMerchantUserId().equals(userId)) {
            log.error("he order does not belong to user, userId: {} transactionNo: {}", userId, transactionNo);
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
                log.warn("The transaction status is successful, but the payment time is empty. transaction number: {}"
                        , payOrderDto.getMerchantOrderNo());
            }
        }

        return CommonResponse.success(result);
    }

    @Override
    public CommonResponse handleNotify(String currency, String body) throws PakGoPayException {
        log.info("handleNotify start, currency={}, bodySize={}", currency, body == null ? 0 : body.length());
        OrderHandler handler = OrderHandlerFactory.get(
                OrderType.PAY_OUT_ORDER, OrderScope.THIRD_PARTY, currency);
        NotifyRequest response = handler.handleNotify(body);
        log.info("notify parsed, transactionNo={}, merchantNo={}, status={}",
                response.getTransactionNo(), response.getMerchantNo(), response.getStatus());
        OrderHandler.validateNotifyResponse(response);
        log.info("notify response validated, transactionNo={}", response.getTransactionNo());
        PayOrderDto payOrderDto = validateNotifyOrder(response);
        log.info("notify order validated, transactionNo={}, orderStatus={}",
                payOrderDto.getTransactionNo(), payOrderDto.getOrderStatus());
        applyNotifyUpdate(payOrderDto, response);
        log.info("notify applied, transactionNo={}, status={}", payOrderDto.getTransactionNo(), response.getStatus());
        return CommonResponse.success(response);
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
        if(!merchantCheckService.existsPayMerchantOrderNo(payOutOrderRequest.getMerchantOrderNo())){
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

    private PayOrderDto validateNotifyOrder(NotifyRequest response) throws PakGoPayException {
        log.info("validateNotifyOrder start, transactionNo={}", response.getTransactionNo());
        PayOrderDto payOrderDto = payOrderMapper.findByTransactionNo(response.getTransactionNo())
                .orElseThrow(() -> new PakGoPayException(ResultCode.MERCHANT_ORDER_NO_NOT_EXISTS,
                        "record is not exists, transactionNo:" + response.getTransactionNo()));
        if (payOrderDto.getMerchantUserId() == null
                || !payOrderDto.getMerchantUserId().equals(response.getMerchantNo())) {
            throw new PakGoPayException(ResultCode.ORDER_PARAM_VALID, "merchantNo does not match");
        }
        if (TransactionStatus.SUCCESS.getCode().toString().equals(payOrderDto.getOrderStatus())
                || TransactionStatus.FAILED.getCode().toString().equals(payOrderDto.getOrderStatus())) {
            throw new PakGoPayException(ResultCode.ORDER_PARAM_VALID, "order status can not be changed");
        }
        log.info("validateNotifyOrder success, transactionNo={}, orderStatus={}",
                payOrderDto.getTransactionNo(), payOrderDto.getOrderStatus());
        return payOrderDto;
    }

    private void applyNotifyUpdate(PayOrderDto payOrderDto, NotifyRequest response) throws PakGoPayException {
        log.info("applyNotifyUpdate start, transactionNo={}", payOrderDto.getTransactionNo());
        TransactionStatus targetStatus = resolveNotifyStatus(response.getStatus());
        MerchantInfoDto merchantInfo = merchantService.fetchMerchantInfo(payOrderDto.getMerchantUserId());
        if (merchantInfo == null) {
            throw new PakGoPayException(ResultCode.USER_IS_NOT_EXIST);
        }

        PayOrderDto update = new PayOrderDto();
        update.setTransactionNo(payOrderDto.getTransactionNo());
        update.setOrderStatus(targetStatus.getCode().toString());
        if (TransactionStatus.SUCCESS.equals(targetStatus)) {
            update.setSuccessCallbackTime(System.currentTimeMillis() / 1000);
        }
        update.setUpdateTime(System.currentTimeMillis() / 1000);

        transactionUtil.runInTransaction(() -> {
            try {
                int updated = payOrderMapper.updateByTransactionNoWhenProcessing(
                        update, TransactionStatus.PROCESSING.getCode().toString());
                if (updated <= 0) {
                    log.info("payout notify skipped, order not processing, transactionNo={}, status={}",
                            payOrderDto.getTransactionNo(), targetStatus.getCode());
                    return;
                }
                log.info("pay order updated, transactionNo={}, status={}",
                        payOrderDto.getTransactionNo(), targetStatus.getCode());
            } catch (Exception e) {
                log.error("pay order updateByTransactionNo failed, message {}", e.getMessage());
                throw new PakGoPayException(ResultCode.DATA_BASE_ERROR);
            }

            BigDecimal payoutAmount = resolveOrderAmount(payOrderDto.getActualAmount(), payOrderDto.getAmount());
            BigDecimal merchantFee = payOrderDto.getMerchantFee() == null ? BigDecimal.ZERO : payOrderDto.getMerchantFee();
            BigDecimal frozenAmount = CommonUtil.safeAdd(payoutAmount, merchantFee);

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
    }


    private Object dispatchPayOutOrder(PayOrderDto dto, PayOutOrderRequest request) {
        OrderScope scope = Integer.valueOf(1).equals(dto.getPaymentMode())
                ? OrderScope.THIRD_PARTY
                : OrderScope.SYSTEM;
        OrderHandler handler = OrderHandlerFactory.get(
                OrderType.PAY_OUT_ORDER, scope, dto.getCurrencyType());
        Map<String, Object> payload = new HashMap<>();
        payload.put("transactionNo", dto.getTransactionNo());
        payload.put("amount", dto.getAmount());
        payload.put("currency", dto.getCurrencyType());
        payload.put("merchantOrderNo", dto.getMerchantOrderNo());
        payload.put("merchantUserId", dto.getMerchantUserId());
        payload.put("callbackUrl", dto.getCallbackUrl());
        payload.put("bankCode", request.getBankCode());
        payload.put("accountName", request.getAccountName());
        payload.put("accountNo", request.getAccountNo());
        // TODO pending: resolve channel code for payout handler
        payload.put("channelCode", "digimone");
        payload.put("channelParams", request.getChannelParams());
        log.info("dispatchPayOutOrder payload ready, transactionNo={}, paymentMode={}, currency={}",
                dto.getTransactionNo(), dto.getPaymentMode(), dto.getCurrencyType());
        return handler.handle(payload);
    }

    private Map<String, Object> buildPayOutResponse(PayOrderDto dto, Object handlerResponse) {
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

    private PayOrderDto buildPayOrderDto(
            PayOutOrderRequest request,
            TransactionInfo transactionInfo) {
        long now = System.currentTimeMillis() / 1000;
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
        .obj(() -> 1, dto::setOrderType) // order type: 1-system, 2-manual
        .obj(() -> OrderStatus.PROCESSING.getCode().toString(), dto::setOrderStatus) // order status: 1-processing, 2-failed
        .obj(request::getNotificationUrl, dto::setCallbackUrl) // async callback url
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
        // operateType
        log.info("buildPayOrderDto finish, transactionNo={}, merchantUserId={}, paymentId={}, channelId={}",
                dto.getTransactionNo(), dto.getMerchantUserId(), dto.getPaymentId(), dto.getChannelId());
        return builder.build();
    }
}
