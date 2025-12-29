package com.pakgopay.common.http;

import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.ResponseErrorHandler;

import java.io.IOException;

public class PaKGoPayCustomResponseErrorHandler implements ResponseErrorHandler {

    @Override
    public boolean hasError(ClientHttpResponse response) throws IOException {
        return response.getStatusCode().isError();
    }

    @Override
    public void handleError(ClientHttpResponse response) throws IOException {
        int status = response.getRawStatusCode();

        if (status >= 400 && status < 500) {
            throw new RuntimeException("HTTP 4xx error: " + status);
        }
        if (status >= 500) {
            throw new RuntimeException("HTTP 5xx error: " + status);
        }
    }
}

