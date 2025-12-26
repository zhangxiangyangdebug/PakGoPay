package com.pakgopay.mapper.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class AgentInfoDto {

    /** xiaoyou 用户id */
    private Long userId;

    /** xiaoyou 所属代理userId */
    private Long parentId;

    /** xiaoyou 商户昵称 */
    private String merchantName;

    /** xiaoyou 是否支持代收 */
    private Integer collectionEnabled;

    /** xiaoyou 是否支持代付 */
    private Integer payEnabled;

    /** xiaoyou 代收日限额 */
    private BigDecimal dailyCollectionLimit;

    /** xiaoyou 代付日限额 */
    private BigDecimal dailyPayLimit;

    /** xiaoyou 代收月限额 */
    private BigDecimal monthlyCollectionLimit;

    /** xiaoyou 代付月限额 */
    private BigDecimal monthlyPayLimit;

    /** xiaoyou 启用状态0-停用，1-启用 */
    private Integer status;

    /** xiaoyou 风险等级 */
    private Integer riskLevel;

    /** xiaoyou 是否开启通知 */
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

    /** xiaoyou 代收固定费用 */
    private BigDecimal collectionFixedFee;

    /** xiaoyou 代收最高费用 */
    private BigDecimal collectionMaxFee;

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

    /** xiaoyou 版本号 */
    private Integer version;

    /** xiaoyou 是否有代理0：没有、1：有 */
    private Integer isAgent;

    /** xiaoyou 是否开启金额浮动0：关闭、1：开启 */
    private Integer isFloat;

    /** xiaoyou 代收ip白名单 */
    private String colWhiteIps;

    /** xiaoyou 代付ip白名单 */
    private String payWhiteIps;
}
