package com.pakgopay.mapper.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class CollectionOrderDetailDto {

    /** xiaoyou 订单id */
    private String orderId;

    /** xiaoyou 订单金额 */
    private BigDecimal amount;

    /** xiaoyou 实际金额 */
    private BigDecimal actualAmount;

    /** xiaoyou 浮动金额 */
    private BigDecimal floatingAmount;

    /** xiaoyou 币种id */
    private Long currencyId;

    /** xiaoyou 用户id */
    private String userId;

    /** xiaoyou 回调token */
    private String callbackToken;

    /** xiaoyou 回调地址 */
    private String callbackUrl;

    /** xiaoyou 回调次数 */
    private Integer callbackTimes;

    /** xiaoyou 上次回调时间 */
    private LocalDateTime lastCallbackTime;

    /** xiaoyou 固定费用 */
    private BigDecimal fixedFee;

    /** xiaoyou 商户费率 */
    private BigDecimal merchantRate;

    /** xiaoyou 商户费用 */
    private BigDecimal merchantFee;

    /** xiaoyou 一级代理费率 */
    private BigDecimal agent1Rate;

    /** xiaoyou 一级代理固定费用 */
    private String agent1FixedFee;

    /** xiaoyou 一级代理佣金 */
    private String agent1Fee;

    /** xiaoyou 二级代理费率 */
    private String agent2Rate;

    /** xiaoyou 二级代理固定费用 */
    private String agent2FixedFee;

    /** xiaoyou 二级代理费用 */
    private String agent2Fee;

    /** xiaoyou 三级代理费率 */
    private String agent3Rate;

    /** xiaoyou 三级代理固定费用 */
    private String agent3FixedFee;

    /** xiaoyou 三级代理费用 */
    private String agent3Fee;

    /** xiaoyou 请求IP */
    private String requestIp;

    /** xiaoyou 订单确认类型：1-系统完成、2-手工确认 */
    private String operateType;

    /** xiaoyou 备注 */
    private String remark;

    /** xiaoyou 创建时间 */
    private LocalDateTime createTime;

    /** xiaoyou 更新时间 */
    private LocalDateTime updateTime;

    /** xiaoyou 1:三方支付、2：系统支付 */
    private Integer collectionMode;

    /** xiaoyou 通道id */
    private Long paymentId;
}
