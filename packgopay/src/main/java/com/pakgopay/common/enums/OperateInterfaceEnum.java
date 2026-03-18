package com.pakgopay.common.enums;

import lombok.Getter;

@Getter
public enum OperateInterfaceEnum {
    // Agent
    ADD_AGENT(3, "Create Agent"),
    EDIT_AGENT(1, "Update Agent"),
    ADD_AGENT_ACCOUNT(3, "Create Agent Account"),
    EDIT_AGENT_ACCOUNT(1, "Update Agent Account"),

    // Channel / Payment
    ADD_CHANNEL(3, "Create Channel"),
    EDIT_CHANNEL(1, "Update Channel"),
    ADD_PAYMENT(3, "Create Payment"),
    EDIT_PAYMENT(1, "Update Payment"),

    // Currency
    ADD_CURRENCY_TYPE(3, "Create Currency Type"),
    SYNC_CURRENCY_TYPE(1, "Sync Currency Type"),
    UPDATE_CURRENCY_TYPE(1, "Update Currency Type"),

    // Auth
    LOGIN(1, "User Login"),
    LOGOUT(1, "User Logout"),

    // Merchant / Account
    ADD_MERCHANT(3, "Create Merchant"),
    EDIT_MERCHANT(1, "Update Merchant"),
    ADD_MERCHANT_ACCOUNT(3, "Create Merchant Account"),
    EDIT_MERCHANT_ACCOUNT(1, "Update Merchant Account"),
    CREATE_ACCOUNT_STATEMENT(3, "Create Account Statement"),
    EDIT_ACCOUNT_STATEMENT(1, "Update Account Statement"),
    RESET_MERCHANT_SIGN_KEY(1, "Reset Merchant Sign Key"),

    // Report export
    EXPORT_MERCHANT_REPORT(4, "Export Merchant Report"),
    EXPORT_CHANNEL_REPORT(4, "Export Channel Report"),
    EXPORT_AGENT_REPORT(4, "Export Agent Report"),
    EXPORT_CURRENCY_REPORT(4, "Export Currency Report"),
    EXPORT_PAYMENT_REPORT(4, "Export Payment Report"),

    // System config
    CREATE_USER(3, "Create User"),
    EDIT_LOGIN_USER(1, "Edit User"),
    MANAGE_LOGIN_USER_STATUS(1, "Update User Status"),
    DELETE_LOGIN_USER(2, "Delete User"),
    ADD_ROLE(3, "Create Role"),
    MODIFY_ROLE(1, "Update Role"),
    DELETE_ROLE(2, "Delete Role"),
    RESET_GOOGLE_KEY(1, "Reset Google Key"),
    BIND_GOOGLE_KEY(1, "Bind Google Key"),
    UPDATE_TELEGRAM_CONFIG(1, "Update Telegram Config"),
    TELEGRAM_BROADCAST(1, "Telegram Broadcast"),
    UPDATE_RATE_LIMIT_CONFIG(1, "Update Rate Limit Config"),
    UPDATE_COLLECTION_CONFIG(1, "Update Collection Config"),
    UPDATE_PAYOUT_CONFIG(1, "Update Payout Config"),

    // Transaction manual operation
    MANUAL_CREATE_COLLECTION_ORDER(3, "Manual Create Collection Order"),
    MANUAL_CREATE_PAYOUT_ORDER(3, "Manual Create Payout Order"),
    MANUAL_NOTIFY_COLLECTION_ORDER(1, "Manual Collection Notify"),
    MANUAL_NOTIFY_PAYOUT_ORDER(1, "Manual Payout Notify"),
    MANUAL_REVERSE_COLLECTION_ORDER(1, "Manual Reverse Collection Order"),
    MANUAL_REVERSE_PAYOUT_ORDER(1, "Manual Reverse Payout Order");

    /**
     * Operate type:
     * 1 = update
     * 2 = delete
     * 3 = create
     * 4 = export
     */
    private final Integer operateType;
    private final String message;

    OperateInterfaceEnum(Integer operateType, String message) {
        this.operateType = operateType;
        this.message = message;
    }
}
