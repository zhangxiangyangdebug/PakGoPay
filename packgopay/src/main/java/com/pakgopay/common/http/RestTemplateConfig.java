package com.pakgopay.common.http;

import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.util.Timeout;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate() {

        // ===== connection pool =====
        var connManager =
                new PoolingHttpClientConnectionManager();
        connManager.setMaxTotal(200);          // 总连接数
        connManager.setDefaultMaxPerRoute(100); // 单路由最大连接

        // ===== request time out config =====
        var requestConfig =
                RequestConfig.custom()
                        .setConnectTimeout(Timeout.ofMilliseconds(500))
                        .setResponseTimeout(Timeout.ofMilliseconds(25000))
                        .build();

        var httpClient =
                HttpClients.custom()
                        .setConnectionManager(connManager)
                        .setDefaultRequestConfig(requestConfig)
                        .build();

        var factory =
                new HttpComponentsClientHttpRequestFactory(httpClient);

        // Spring time out config（ >= HttpClient timeout time）
        factory.setConnectTimeout(500);
        factory.setReadTimeout(25000);

        RestTemplate restTemplate = new RestTemplate(factory);

        // ===== interceptor（log/auth）=====
        restTemplate.getInterceptors().add(new PakGoPayHttpLoggerInterceptor());

        // ===== error handler =====
        restTemplate.setErrorHandler(new PaKGoPayCustomResponseErrorHandler());

        return restTemplate;
    }
}

