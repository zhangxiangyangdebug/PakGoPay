package com.pakgopay.common.http;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.*;
import java.io.IOException;

@Slf4j
public class PakGoPayHttpLoggerInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(
            HttpRequest request,
            byte[] body,
            ClientHttpRequestExecution execution) throws IOException {

        long start = System.currentTimeMillis();
        try {
            return execution.execute(request, body);
        } finally {
            long cost = System.currentTimeMillis() - start;
            // 建议换成 logger
            log.info("[HTTP] {} {} cost={}ms", request.getMethod(), request.getURI(), cost);
        }
    }
}
