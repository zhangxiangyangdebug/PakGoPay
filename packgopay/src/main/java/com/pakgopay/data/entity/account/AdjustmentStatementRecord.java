package com.pakgopay.data.entity.account;

import java.math.BigDecimal;

/**
 * Payload object for writing one adjustment statement row.
 */
public record AdjustmentStatementRecord(
        Subject subject,
        Audit audit) {

    /**
     * Business subject and adjustment amount.
     */
    public record Subject(
            String userId,
            Integer userRole,
            String name,
            String currency,
            BigDecimal amount) {
    }

    /**
     * Audit metadata used by account statement.
     */
    public record Audit(
            String requestIp,
            String operator,
            String remark) {
    }
}
