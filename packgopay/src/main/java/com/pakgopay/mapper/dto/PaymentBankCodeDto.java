package com.pakgopay.mapper.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

@Data
@EqualsAndHashCode(callSuper = true)
public class PaymentBankCodeDto extends BankCodeDictDto {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Whether this bank code is bound to the current payment channel.
     */
    private Boolean selected;

    /**
     * Support type of current relation. 0=collection,1=payout,2=both.
     */
    private Integer supportType;

    /**
     * Status for current payment-channel bank-code relation.
     */
    private Integer status;
}
