package com.pakgopay.demo;

import java.util.HashMap;
import java.util.Map;

public class AppConfig {
    private String baseUrl;
    private String token;
    private String signKey;
    private String queryPath;
    private Map<String, Map<String, String>> endpointParamConfig = new HashMap<>();

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getQueryPath() {
        return queryPath;
    }

    public void setQueryPath(String queryPath) {
        this.queryPath = queryPath;
    }

    public String getSignKey() {
        return signKey;
    }

    public void setSignKey(String signKey) {
        this.signKey = signKey;
    }

    public Map<String, Map<String, String>> getEndpointParamConfig() {
        return endpointParamConfig;
    }

    public void setEndpointParamConfig(Map<String, Map<String, String>> endpointParamConfig) {
        this.endpointParamConfig = endpointParamConfig;
    }

    public static AppConfig defaults() {
        AppConfig cfg = new AppConfig();
        cfg.setBaseUrl("http://127.0.0.1:8080");
        cfg.setToken("");
        cfg.setSignKey("");
        cfg.setQueryPath("/pakGoPay/api/server/v1/queryOrder");
        cfg.setEndpointParamConfig(new HashMap<>());
        return cfg;
    }
}
