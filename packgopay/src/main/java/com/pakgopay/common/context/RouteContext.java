package com.pakgopay.common.context;

import com.pakgopay.common.enums.OrderType;
import com.pakgopay.common.enums.CountryCode;
import com.pakgopay.common.enums.OrderScope;

public class RouteContext {
    private final OrderType orderType;   // 代收/代付
    private final OrderScope scope;     // SYSTEM / THIRD_PARTY
    private final String channelCode;   // 可选：三方内再按具体通道编码
    private final CountryCode country;  // 国家类型

    public RouteContext(OrderType orderType, OrderScope scope, String channelCode, CountryCode country) {
        this.orderType = orderType;
        this.country = country;
        this.scope = scope;
        this.channelCode = channelCode;
    }


    public OrderType getOrderType() {
        return orderType;
    }

    public OrderScope getScope() {
        return scope;
    }

    public String getChannelCode() {
        return channelCode;
    }

    public CountryCode getCountry() {
        return country;
    }

    @Override
    public String toString() {
        return "RouteContext{" + orderType + "," + scope + "," + channelCode + "}";
    }
}
