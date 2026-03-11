package com.pakgopay.data.entity.account;

import com.pakgopay.mapper.dto.BalanceDto;

import java.math.BigDecimal;

/**
 * Payload object for writing one adjustment statement row.
 */
public record AdjustmentStatementRecord(
        Subject subject,
        Snapshot snapshot,
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
     * Balance snapshots before/after adjustment.
     */
    public record Snapshot(
            BalanceDto before,
            BalanceDto after) {
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
