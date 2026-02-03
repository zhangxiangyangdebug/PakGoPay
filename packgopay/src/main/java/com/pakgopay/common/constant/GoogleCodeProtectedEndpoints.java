package com.pakgopay.common.constant;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class GoogleCodeProtectedEndpoints {
    private static final Map<String, String> ENDPOINTS;

    static {
        Map<String, String> map = new LinkedHashMap<>();

        // System config (user/role management)
        map.put("POST /pakGoPay/server/SystemConfig/createUser", "create user");
        map.put("GET /pakGoPay/server/SystemConfig/manageLoginUserStatus", "update user status");
        map.put("GET /pakGoPay/server/SystemConfig/deleteLoginUser", "delete user");
        map.put("POST /pakGoPay/server/SystemConfig/addRole", "add role");
        map.put("POST /pakGoPay/server/SystemConfig/modifyRoleInfo", "modify role");
        map.put("POST /pakGoPay/server/SystemConfig/deleteRole", "delete role");
        map.put("GET /pakGoPay/server/SystemConfig/resetGoogleKey", "reset google key");

        // Channel & payment management
        map.put("POST /pakGoPay/server/addChannel", "add channel");
        map.put("POST /pakGoPay/server/editChannel", "edit channel");
        map.put("POST /pakGoPay/server/addPayment", "add payment");
        map.put("POST /pakGoPay/server/editPayment", "edit payment");

        // Merchant management
        map.put("POST /pakGoPay/server/merchant/addMerchant", "add merchant");
        map.put("POST /pakGoPay/server/merchant/editMerchant", "edit merchant");
        map.put("POST /pakGoPay/server/merchant/addMerchantAccount", "add merchant account");
        map.put("POST /pakGoPay/server/merchant/editMerchantAccount", "edit merchant account");
        map.put("POST /pakGoPay/server/merchant/createAccountStatement", "create account statement");
        map.put("POST /pakGoPay/server/merchant/editAccountStatement", "edit account statement");

        // Agent management
        map.put("POST /pakGoPay/server/addAgent", "add agent");
        map.put("POST /pakGoPay/server/editAgent", "edit agent");
        map.put("POST /pakGoPay/server/addAgentAccount", "add agent account");
        map.put("POST /pakGoPay/server/editAgentAccount", "edit agent account");

        // Currency type management
        map.put("POST /pakGoPay/server/CurrencyTypeManagement/addCurrencyType", "add currency type");

        // Transaction create orders
        map.put("POST /pakGoPay/server/v1/createCollectionOrder", "create collection order");
        map.put("POST /pakGoPay/server/v1/createPayOutOrder", "create payout order");

        ENDPOINTS = Collections.unmodifiableMap(map);
    }

    private GoogleCodeProtectedEndpoints() {
    }

    public static boolean requiresGoogleCode(String method, String uri) {
        if (method == null || uri == null) {
            return false;
        }
        return ENDPOINTS.containsKey(method.toUpperCase() + " " + uri);
    }

    public static Map<String, String> getEndpoints() {
        return ENDPOINTS;
    }
}
