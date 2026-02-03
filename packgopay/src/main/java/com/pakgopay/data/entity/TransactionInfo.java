package com.pakgopay.data.entity;

import com.pakgopay.mapper.dto.ChannelDto;
import com.pakgopay.mapper.dto.MerchantInfoDto;
import com.pakgopay.mapper.dto.PaymentDto;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class TransactionInfo {

    /**
     * system generator order id
     * (Collection: start with COLL)
     * (Pay out: start with PAY)
     */
    private String transactionNo;

    // ------------------------ merchant info ---------------------------------
    /**
     * Merchant Id
     */
    private String merchantId;

    /**
     * Merchant order number (must be unique)
     */
    private String merchantOrderNo;

    /**
     * Merchant detail info
     */
    private MerchantInfoDto merchantInfo;

    /**
     * Merchant Fee
     */
    private BigDecimal merchantFee;

    /**
     * Merchant Rate
     */
    private BigDecimal merchantRate;

    /**
     * Merchant Fixed Fee
     */
    private BigDecimal merchantFixedFee;

    // ------------------------ merchant info ---------------------------------
    /** Level 1 agent rate */
    private BigDecimal agent1Rate;

    /** Level 1 agent fixed fee */
    private BigDecimal agent1FixedFee;

    /** Level 1 agent commission */
    private BigDecimal agent1Fee;

    /** Level 2 agent rate */
    private BigDecimal agent2Rate;

    /** Level 2 agent fixed fee */
    private BigDecimal agent2FixedFee;

    /** Level 2 agent commission */
    private BigDecimal agent2Fee;

    /** Level 3 agent rate */
    private BigDecimal agent3Rate;

    /** Level 3 agent fixed fee */
    private BigDecimal agent3FixedFee;

    /** Level 3 agent commission */
    private BigDecimal agent3Fee;

    // ------------------------ channel info ---------------------------------

    /**
     * Merchant channel code (must be unique)
     */
    private Integer paymentNo;

    /**
     * use payment id
     */
    private Long paymentId;

    /**
     * use payment info
     */
    private PaymentDto paymentInfo;

    /**
     * use channel id
     */
    private Long channelId;

    /**
     * use channel info
     */
    private ChannelDto channelInfo;

    // ------------------------ amount info ---------------------------------

    /**
     * Collection amount
     */
    private BigDecimal amount;

    /**
     * Actual amount used for fee calculation
     */
    private BigDecimal actualAmount;

    /**
     * Currency code (e.g. VND, PKR, IDR, USD, CNY)
     */
    private String currency;

    // ------------------------ notification info ---------------------------------

    /**
     * Asynchronous notification URL
     */
    private String notificationUrl;

    // ------------------------ common info ---------------------------------

    /**
     * request ip
     */
    private String requestIp;

}
