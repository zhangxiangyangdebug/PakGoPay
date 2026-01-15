package com.pakgopay.data.reqeust.agent;

import com.pakgopay.data.reqeust.BaseRequest;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class AgentEditRequest extends BaseRequest {

    /**
     * Agent name
     */
    private String agentName;

    /**
     * status
     */
    private Integer status;

    /**
     * statusA
     */
    private List<Integer> channelIds;

    /** Contact name */
    private String contactName;

    /** Contact email */
    private String contactEmail;

    /** Contact phone */
    private String contactPhone;

    /** Collection rate */
    private BigDecimal collectionRate;

    /** Collection fixed fee */
    private BigDecimal collectionFixedFee;

    /** Collection max fee */
    private BigDecimal collectionMaxFee;

    /** Collection min fee */
    private BigDecimal collectionMinFee;

    /** Payout rate */
    private BigDecimal payRate;

    /** Payout fixed fee */
    private BigDecimal payFixedFee;

    /** Payout max fee */
    private BigDecimal payMaxFee;

    /** Payout min fee */
    private BigDecimal payMinFee;

    /** Login white ips */
    private String loginIps;

    /** withdraw ips */
    private String withdrawIps;
}
