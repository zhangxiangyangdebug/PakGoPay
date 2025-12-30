package com.pakgopay.common.entity;

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
    private String orderId;

    // ------------------------ merchant info ---------------------------------
    /**
     * Merchant Id
     */
    private String merchant_id;

    /**
     * Merchant order number (must be unique)
     */
    private String merchantOrderNo;

    /**
     * Merchant detail info
     */
    private MerchantInfoDto merchantInfo;

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
     * Currency code (e.g. VND, PKR, IDR, USD, CNY)
     */
    private String currency;

    // ------------------------ notification info ---------------------------------

    /**
     * Asynchronous notification URL
     */
    private String notificationUrl;

}
