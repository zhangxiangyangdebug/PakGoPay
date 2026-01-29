package com.pakgopay.mapper.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

@Data
public class AgentInfoDto implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Agent number */
    private Long agentNo;

    /** Agent name */
    private String agentName;

    /** Agent login user ID */
    private String userId;

    /** Account name */
    private String accountName;

    /** Contact name */
    private String contactName;

    /** Contact email */
    private String contactEmail;

    /** Contact phone */
    private String contactPhone;

    /** Parent agent ID */
    private String parentId;

    /** First level agent ID */
    private String topAgentId;

    /** Agent's channel ids */
    private String channelIds;

    /** Status: 0-disabled, 1-enabled */
    private Integer status;

    /** Agent level */
    private Integer level;

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

    /** Create time */
    private Long createTime;

    /** Created by */
    private String createBy;

    /** Update time */
    private Long updateTime;

    /** Updated by (DB column: upeate_by) */
    private String updateBy;

    /** Version */
    private Integer version;

    /** Remark */
    private String remark;

    //-------------------------Extended information------------------------------------------
    /** parent agent name */
    private String parentAgentName;

    /** parent user name */
    private String parentUserName;

    /** agent's parent channel Info */
    private List<ChannelDto> parentChannelDtoList;

    /** agent's channel Info */
    private List<ChannelDto> channelDtoList;

    /** agent's channel id list */
    private List<Long> channelIdList;
}
