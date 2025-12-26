package com.pakgopay.mapper.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class MerchantInfoDto {

    /** xiaoyou 用户ID */
    private Long userId;

    /** xiaoyou 上级ID */
    private Long parentId;

    /** xiaoyou 商户名称 */
    private String merchantName;

    /** xiaoyou 是否开启代收（0-否 1-是） */
    private Integer collectionEnabled;

    /** xiaoyou 是否开启代付（0-否 1-是） */
    private Integer payEnabled;

    /** xiaoyou 每日代收限额 */
    private BigDecimal dailyCollectionLimit;

    /** xiaoyou 每日代付限额 */
    private BigDecimal dailyPayLimit;

    /** xiaoyou 每月代收限额 */
    private BigDecimal monthlyCollectionLimit;

    /** xiaoyou 每月代付限额 */
    private BigDecimal monthlyPayLimit;

    /** xiaoyou 状态（0-禁用 1-启用） */
    private Integer status;

    /** xiaoyou 风险等级 */
    private Integer riskLevel;

    /** xiaoyou 是否开启通知（0-否 1-是） */
    private Integer notificationEnable;

    /** xiaoyou 创建时间 */
    private LocalDateTime createTime;

    /** xiaoyou 创建人 */
    private String createBy;

    /** xiaoyou 更新时间 */
    private LocalDateTime updateTime;

    /** xiaoyou 更新人 */
    private String updateBy;

    /** xiaoyou 代收费率 */
    private BigDecimal collectionRate;

    /** xiaoyou 代收固定手续费 */
    private BigDecimal collectionFixedFee;

    /** xiaoyou 代收最高手续费 */
    private BigDecimal collectionMaxFee;

    /** xiaoyou 代收最低手续费 */
    private BigDecimal collectionMinFee;

    /** xiaoyou 代付费率 */
    private BigDecimal payRate;

    /** xiaoyou 代付固定手续费 */
    private BigDecimal payFixedFee;

    /** xiaoyou 代付最高手续费 */
    private BigDecimal payMaxFee;

    /** xiaoyou 代付最低手续费 */
    private BigDecimal payMinFee;

    /** xiaoyou 版本号（乐观锁） */
    private Integer version;

    /** xiaoyou 是否代理（0-否 1-是） */
    private Integer isAgent;

    /** xiaoyou 是否浮动（0-否 1-是） */
    private Integer isFloat;

    /** xiaoyou 代收 IP 白名单（逗号分隔） */
    private String colWhiteIps;

    /** xiaoyou 代付IP 白名单（逗号分隔） */
    private String payWhiteIps;

    /** xiaoyou 渠道ID（逗号分隔） */
    private String channelIds;
}
