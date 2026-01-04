package com.pakgopay.service.order.impl;

import com.pakgopay.common.constant.CommonConstant;
import com.pakgopay.common.entity.TransactionInfo;
import com.pakgopay.common.enums.OrderStatus;
import com.pakgopay.common.enums.OrderType;
import com.pakgopay.common.enums.ResultCode;
import com.pakgopay.common.exception.PakGoPayException;
import com.pakgopay.common.reqeust.transaction.PayOutOrderRequest;
import com.pakgopay.common.response.CommonResponse;
import com.pakgopay.mapper.PayOrderMapper;
import com.pakgopay.mapper.dto.MerchantInfoDto;
import com.pakgopay.mapper.dto.PayOrderDto;
import com.pakgopay.service.order.ChannelPaymentService;
import com.pakgopay.service.order.MerchantCheckService;
import com.pakgopay.service.order.PayOutOrderService;
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
public class PayOutOrderServiceImpl implements PayOutOrderService {

    @Autowired
    private MerchantCheckService merchantCheckService;

    @Autowired
    private ChannelPaymentService channelPaymentService;

    private PayOrderMapper payOrderMapper;

    @Override
    public CommonResponse createPayOutOrder(PayOutOrderRequest payOrderRequest) throws PakGoPayException {
        log.info("createPayOutOrder start");
        TransactionInfo transactionInfo = new TransactionInfo();
        // 1. get merchant info
        MerchantInfoDto merchantInfoDto = merchantCheckService.getMerchantInfo(payOrderRequest.getUserId());
        transactionInfo.setMerchantInfo(merchantInfoDto);
        // merchant is not exists
        if (merchantInfoDto == null) {
            log.error("merchant info is not exist, userId {}", payOrderRequest.getUserId());
            throw new PakGoPayException(ResultCode.USER_IS_NOT_EXIST);
        }

        // 2. check request validate
        validatePayOutRequest(payOrderRequest, merchantInfoDto);

        // 3. get available payment id
        transactionInfo.setCurrency(payOrderRequest.getCurrency());
        transactionInfo.setAmount(payOrderRequest.getAmount());
        transactionInfo.setPaymentNo(payOrderRequest.getPaymentNo());
        Long paymentId = channelPaymentService.getPaymentId(
                merchantInfoDto.getChannelIds(), CommonConstant.SUPPORT_TYPE_PAY, transactionInfo);

        // 4. create system transaction no
        String systemTransactionNo = SnowflakeIdGenerator.getSnowFlakeId(CommonConstant.PAYOUT_PREFIX);
        log.info("generator system transactionNo :{}", systemTransactionNo);
        transactionInfo.setTransactionNo(systemTransactionNo);

        // 5. calculate transaction fee
        // xiaoyou TODO 计算渠道和通道费率和成本
        // 计算多级代理商分成（判断是否有代理 is_agent）
        // 计算平台利润 = 商户抽成 - 渠道成本（渠道和通道） - 代理商分成
        channelPaymentService.calculateTransactionFee(transactionInfo, OrderType.PAY_OUT_ORDER);

        // 验证商户可用余额
        // 冻结资金
        // 设置付款账户信息

        return null;
    }

    @Override
    public CommonResponse queryOrderInfo(String userId, String transactionNo) throws PakGoPayException {
        log.info("queryOrderInfo start");
        // query collection order info  from db
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
        if (!payOrderDto.getMerchantId().equals(userId)) {
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
        if (OrderStatus.FAILED.getCode().equals(payOrderDto.getOrderStatus())) {
            result.put("failureReason", "");
        }

        if (OrderStatus.SUCCESS.getCode().equals(payOrderDto.getOrderStatus())) {
            LocalDateTime successCallBackTime = payOrderDto.getSuccessCallbackTime();
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

    private void validatePayOutRequest(
            PayOutOrderRequest payOutOrderRequest, MerchantInfoDto merchantInfoDto) throws PakGoPayException {
        log.info("validatePayOutRequest start");
        // check ip white list
        if (merchantCheckService.isPayIpAllowed(
                payOutOrderRequest.getUserId(), payOutOrderRequest.getClientIp(), merchantInfoDto.getColWhiteIps())) {
            log.error("isColIpAllowed failed, clientIp: {}", payOutOrderRequest.getClientIp());
            throw new PakGoPayException(ResultCode.IS_NOT_WHITE_IP);
        }

        // check Merchant order code is uniqueness
        if(merchantCheckService.existsPayMerchantOrderNo(payOutOrderRequest.getMerchantOrderNo())){
            log.error("existsColMerchantOrderNo failed, merchantOrderNo: {}", payOutOrderRequest.getMerchantOrderNo());
            throw new PakGoPayException(ResultCode.MERCHANT_CODE_IS_EXISTS);
        }

        // amount check
        if (payOutOrderRequest.getAmount().compareTo(BigDecimal.ZERO) <= CommonConstant.ZERO) {
            log.error("The transaction amount must be greater than 0, amount: {}", payOutOrderRequest.getAmount());
            throw new PakGoPayException(ResultCode.ORDER_PARAM_VALID, "The transaction amount must be greater than 0.");
        }

        // check user is enabled
        if (merchantCheckService.isEnableMerchant(merchantInfoDto.getStatus(), merchantInfoDto.getParentId())) {
            log.error("The merchant status is disable, merchantName: {}", merchantInfoDto.getMerchantName());
            throw new PakGoPayException(ResultCode.USER_NOT_ENABLE);
        }

        // check merchant is support payout
        if (!merchantInfoDto.getPayEnabled()) {
            log.error("The merchant is not support collection, merchantName: {}", merchantInfoDto.getMerchantName());
            throw new PakGoPayException(ResultCode.MERCHANT_NOT_SUPPORT_PAYOUT);
        }
        log.info("validateCollectionRequest success");
    }
}
