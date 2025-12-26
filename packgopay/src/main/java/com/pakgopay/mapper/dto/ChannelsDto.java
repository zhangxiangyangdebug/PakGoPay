package com.pakgopay.mapper.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ChannelsDto {

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

    /** xiaoyou 日代收限额 */
    private BigDecimal dailyCollectionLimit;

    /** xiaoyou 日代付限额 */
    private BigDecimal dailyPayLimit;

    /** xiaoyou 月代付限额（表里是 monthly_collection_limit，注释写月代付限额） */
    private BigDecimal monthlyCollectionLimit;

    /** xiaoyou 最低金额 */
    private BigDecimal minAmount;

    /** xiaoyou 最高金额 */
    private BigDecimal maxAmount;

    /** xiaoyou 月代付限额 */
    private BigDecimal monthlyPayLimit;

    /** xiaoyou 更新时间 */
    private LocalDateTime updateTime;

    /** xiaoyou 更新人 */
    private String updateBy;

    /** xiaoyou 创建时间 */
    private LocalDateTime createTime;

    /** xiaoyou 创建人 */
    private String createBy;

    /** xiaoyou 启用状态0-停用、1-启用 */
    private Integer status;

    /** xiaoyou 代收最高费用 */
    private BigDecimal collectionMaxFee;

    /** xiaoyou 代收费率 */
    private BigDecimal collectionRate;

    /** xiaoyou 代收固定费用 */
    private BigDecimal collectionFixedFee;

    /** xiaoyou 代收最低费用 */
    private BigDecimal collectionMinFee;

    /** xiaoyou 代付费率 */
    private BigDecimal payRate;

    /** xiaoyou 代付固定费用 */
    private BigDecimal payFixedFee;

    /** xiaoyou 代付最高费用 */
    private BigDecimal payMaxFee;

    /** xiaoyou 代付最低费用 */
    private BigDecimal payMinFee;

    /** xiaoyou 备注（表字段名 remark） */
    private String remark;

    /** xiaoyou 渠道关联通道ids（逗号分隔等） */
    private String paymentIds;
}
