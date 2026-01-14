package com.pakgopay.mapper.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

@Data
public class ChannelDto implements Serializable {

    private static final long serialVersionUID = 1L;

    /** xiaoyou 渠道id */
    private Long channelId;

    /** xiaoyou 渠道名称 */
    private String channelName;

    /** xiaoyou 使用总数 */
    private Long totalCount;

    /** xiaoyou 失败数量 */
    private Long failCount;

    /** xiaoyou 成功率 */
    private BigDecimal successRate;

    /** xiaoyou 更新时间 */
    private Long updateTime;

    /** xiaoyou 更新人 */
    private String updateBy;

    /** xiaoyou 创建时间 */
    private Long createTime;

    /** xiaoyou 创建人 */
    private String createBy;

    /** xiaoyou 启用状态0-停用、1-启用 */
    private Integer status;

    /** xiaoyou 备注（表字段名 remark） */
    private String remark;

    /** xiaoyou 渠道关联通道ids（逗号分隔等） */
    private String paymentIds;

    /**
     * payment infos
     */
    private List<PaymentDto> paymentDtoList;
}
