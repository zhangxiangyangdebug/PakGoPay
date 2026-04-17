package com.pakgopay.mapper.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class WithdrawOrderDto implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 自增主键ID
     */
    private Long id;

    /**
     * 提现订单号
     */
    private String withdrawNo;

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 用户角色
     */
    private Integer userRole;

    /**
     * 用户名称/商户名称/代理名称
     */
    private String name;

    /**
     * 币种
     */
    private String currency;

    /**
     * 提现申请金额
     */
    private BigDecimal amount;

    /**
     * 提现钱包地址
     */
    private String walletAddr;

    /**
     * 请求人IP
     */
    private String requestIp;

    /**
     * 提现状态：0待审核，1已驳回，2成功
     */
    private Integer status;

    /**
     * 失败原因
     */
    private String failReason;

    /**
     * 备注
     */
    private String remark;

    /**
     * 创建时间，Unix秒
     */
    private Long createTime;

    /**
     * 更新时间，Unix秒
     */
    private Long updateTime;

    /**
     * 创建人
     */
    private String createBy;

    /**
     * 更新人
     */
    private String updateBy;
}