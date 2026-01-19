package com.pakgopay.data.reqeust.merchant;

import com.pakgopay.data.reqeust.BaseRequest;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class MerchantEditRequest extends BaseRequest {

    /**
     * merchant UserId
     */
    private String merchantUserId;

    /** Parent ID (upper-level ID) */
    private String parentId;

    /** Merchant name */
    private String merchantName;

    /** support type: 0:collect 1:pay 2:collect/pay */
    private Integer supportType;

    /** Status (0 = Disabled, 1 = Enabled) */
    private Integer status;

    /** Risk level */
    private Integer riskLevel;

    /** Whether notification is enabled (0 = No, 1 = Yes) */
    private Integer notificationEnable;

    /** Collection rate */
    private BigDecimal collectionRate;

    /** Collection fixed fee */
    private BigDecimal collectionFixedFee;

    /** Collection maximum fee */
    private BigDecimal collectionMaxFee;

    /** Collection minimum fee */
    private BigDecimal collectionMinFee;

    /** Payout rate */
    private BigDecimal payRate;

    /** Payout fixed fee */
    private BigDecimal payFixedFee;

    /** Payout maximum fee */
    private BigDecimal payMaxFee;

    /** Payout minimum fee */
    private BigDecimal payMinFee;

    /** Whether floating is enabled (0 = No, 1 = Yes) */
    private Integer isFloat;

    /** float range */
    private BigDecimal floatRange;

    /** Collection IP whitelist (comma-separated) */
    private String colWhiteIps;

    /** Payout IP whitelist (comma-separated) */
    private String payWhiteIps;

    /** Channel IDs (comma-separated) */
    private List<Long> channelIds;
}
