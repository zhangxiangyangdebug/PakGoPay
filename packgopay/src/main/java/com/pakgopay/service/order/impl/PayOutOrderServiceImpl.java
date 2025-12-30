package com.pakgopay.service.order.impl;

import com.pakgopay.common.constant.CommonConstant;
import com.pakgopay.common.entity.TransactionInfo;
import com.pakgopay.common.enums.ResultCode;
import com.pakgopay.common.exception.PakGoPayException;
import com.pakgopay.common.reqeust.transaction.PayOutOrderRequest;
import com.pakgopay.common.response.CommonResponse;
import com.pakgopay.mapper.dto.MerchantInfoDto;
import com.pakgopay.service.order.ChannelPaymentService;
import com.pakgopay.service.order.MerchantCheckService;
import com.pakgopay.service.order.PayOutOrderService;
import com.pakgopay.util.SnowflakeIdGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class PayOutOrderServiceImpl implements PayOutOrderService {

    @Autowired
    MerchantCheckService merchantCheckService;

    @Autowired
    ChannelPaymentService channelPaymentService;

    @Override
    public CommonResponse createPayOutOrder(PayOutOrderRequest payOrderRequest) throws PakGoPayException {
        TransactionInfo transactionInfo = new TransactionInfo();
        // 1. get merchant info
        MerchantInfoDto merchantInfoDto = merchantCheckService.getConfigurationInfo(payOrderRequest.getUserId());
        transactionInfo.setMerchantInfo(merchantInfoDto);
        // merchant is not exists
        if (merchantInfoDto == null) {
            throw new PakGoPayException(ResultCode.USER_IS_NOT_EXIST);
        }

        // 2. check request validate
        validatePayOutRequest(payOrderRequest, merchantInfoDto);
        // 3. get available payment id
        Long paymentId = channelPaymentService.getPaymentId(
                payOrderRequest.getPaymentNo(), payOrderRequest.getAmount(),
                merchantInfoDto.getChannelIds(), CommonConstant.SUPPORT_TYPE_PAY, transactionInfo);

        // 4. create system transaction no
        String systemTransactionNo = SnowflakeIdGenerator.getSnowFlakeId("PAY");
        transactionInfo.setOrderId(systemTransactionNo);

        // 5. calculate transaction fee
//        channelPaymentService.calculateTransactionFee();

        return null;
    }

    private void validatePayOutRequest(PayOutOrderRequest payOutOrderRequest, MerchantInfoDto merchantInfoDto) throws PakGoPayException {
        // check ip white list
        if (merchantCheckService.isPayIpAllowed(
                payOutOrderRequest.getUserId(), payOutOrderRequest.getClientIp(), merchantInfoDto.getColWhiteIps())) {
            throw new PakGoPayException(ResultCode.IS_NOT_WHITE_IP);
        }

        // check Merchant order code is uniqueness
        if(merchantCheckService.existsPayMerchantOrderNo(payOutOrderRequest.getMerchantOrderNo())){
            throw new PakGoPayException(ResultCode.MERCHANT_CODE_IS_EXISTS);
        }

        // amount check
        if (payOutOrderRequest.getAmount().compareTo(BigDecimal.ZERO) <= CommonConstant.ZERO) {
            throw new PakGoPayException(ResultCode.ORDER_PARAM_VALID, "The transaction amount must be greater than 0.");
        }

        // check user is enabled
        if (merchantCheckService.isEnableMerchant(merchantInfoDto.getStatus(), merchantInfoDto.getParentId())) {
            throw new PakGoPayException(ResultCode.USER_NOT_ENABLE);
        }
    }
}
