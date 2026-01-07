package com.pakgopay.common.constant;

import java.util.ArrayList;
import java.util.List;

public class CommonConstant {
    // attr client ip
    public static final String ATTR_IP = "CTX_CLIENT_IP";

    // attr user id
    public static final String ATTR_USER_ID = "CTX_USER_ID";

    // xiayou 启用状态-禁用
    public static Integer ENABLE_STATUS_DISABLE = 0;

    // xiaoyou 启用状态-启用
    public static Integer ENABLE_STATUS_ENABLE = 1;

    // xiaoyou 支持类型，代收
    public static Integer SUPPORT_TYPE_COLLECTION = 0;

    // xiaoyou 支持类型，代付
    public static Integer SUPPORT_TYPE_PAY = 1;

    // xiaoyou ZERO
    public static Integer ZERO = 0;

    public static final String USER_INFO_KEY_PREFIX = "user_info";

    public static final String REFRESH_TOKEN_START_TIME_PREFIX = "refresh_token_start_time";

    // collection order no prefix
    public static final String COLLECTION_PREFIX = "COLL";

    // payout order no prefix
    public static final String PAYOUT_PREFIX = "PAY";

    // First level agent
    public static final Integer AGENT_LEVEL_FIRST = 1;

    // Second level agent
    public static final Integer AGENT_LEVEL_SECOND = 2;

    // Third level agent
    public static final Integer AGENT_LEVEL_THIRD = 3;

    // Role unKnown
    public static final int ROLE_UNKNOWN = 0;

    // Role admin
    public static final int ROLE_ADMIN = 1;

    // Role merchant
    public static final int ROLE_MERCHANT = 2;

    // Role finance
    public static final int ROLE_FINANCE = 3;

    // Role agent
    public static final int ROLE_AGENT = 4;

    // merchant report view permissions
    public static final List<Integer> MERCHANT_REPORT_SUPPORT_ROLE = new ArrayList<>() {
        {
            add(ROLE_ADMIN);
            add(ROLE_AGENT);
            add(ROLE_FINANCE);
            add(ROLE_MERCHANT);
        }
    };
}
