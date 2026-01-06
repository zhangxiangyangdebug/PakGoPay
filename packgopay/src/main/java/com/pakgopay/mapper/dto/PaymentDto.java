package com.pakgopay.mapper.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class PaymentDto implements Serializable {

    private static final long serialVersionUID = 1L;

    /** xiaoyou 通道id */
    private Long paymentId;

    /** xiaoyou 通道名称 */
    private String paymentName;

    /** xiaoyou 支持类型0：代收、1：代付、2:代收/代付 */
    private String supportType;

    /** xiaoyou 是否是三方通道0：否、1：是（varchar） */
    private String isThird;

    /** xiaoyou 通道编号 */
    private Integer paymentNo;

    /** xiaoyou 代付费率（varchar） */
    private String paymentPayRate;

    /** xiaoyou 代收费率（varchar） */
    private String paymentCollectionRate;

    /** xiaoyou 代付api接口地址 */
    private String paymentRequestPayUrl;

    /** xiaoyou 代收api接口地址 */
    private String paymentRequestCollectionUrl;

    /** xiaoyou 代付订单校验地址 */
    private String paymentCheckPayUrl;

    /** xiaoyou 代收订单校验地址 */
    private String paymentCheckCollectionUrl;

    /** xiaoyou 最大金额（varchar） */
    private BigDecimal paymentMaxAmount;

    /** xiaoyou 最小金额（varchar） */
    private BigDecimal paymentMinAmount;

    /** xiaoyou 代收日限额（varchar） */
    private BigDecimal collectionDailyLimit;

    /** xiaoyou 代付日限额（varchar） */
    private BigDecimal payDailyLimit;

    /** xiaoyou 代收月限额（varchar） */
    private BigDecimal collectionMonthlyLimit;

    /** xiaoyou 代付月限额（varchar） */
    private BigDecimal payMonthlyLimit;

    /** xiaoyou 代收接口参数 */
    private String collectionInterfaceParam;

    /** xiaoyou 代付接口参数 */
    private String payInterfaceParam;

    /** xiaoyou 代收回调地址 */
    private String collectionCallbackAddr;

    /** xiaoyou 代付回调地址 */
    private String payCallbackAddr;

    /** xiaoyou 币种 */
    private String currencyType;

    /** xiaoyou 创建时间 */
    private Long createTime;

    /** xiaoyou 创建人 */
    private String createBy;

    /** xiaoyou 更新时间 */
    private Long updateTime;

    /** xiaoyou 更新人（表结构里是 datetime，但注释写更新人；按数据库类型映射 unixTime） */
    private Long updateBy;

    /** xiaoyou 备注 */
    private String remark;

    /** xiaoyou 是否需要收银台 */
    private Integer isCheckoutCounter;

    /** xiaoyou 收银台地址 */
    private String checkoutCounterUrl;

    /** xiaoyou 通道类型1：app支付、2：银行卡支付 */
    private String paymentType;

    /** xiaoyou 银行名称 */
    private String bankName;

    /** xiaoyou 银行卡账号 */
    private String bankAccount;

    /** xiaoyou 银行卡姓名 */
    private String bankUserName;
}
