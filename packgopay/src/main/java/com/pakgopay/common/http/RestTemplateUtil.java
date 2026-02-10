package com.pakgopay.common.http;

import com.pakgopay.common.enums.ResultCode;
import com.pakgopay.common.exception.PakGoPayException;
import com.pakgopay.data.response.http.PaymentHttpResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

@Service
public class RestTemplateUtil {
    private static final Logger log = LoggerFactory.getLogger(RestTemplateUtil.class);

    @Autowired
    private RestTemplate restTemplate;

    public PaymentHttpResponse request(HttpEntity entity, HttpMethod method, String url) {
        try {
            ResponseEntity<PaymentHttpResponse> resp =
                    restTemplate.exchange(
                            url,
                            method,
                            entity,
                            PaymentHttpResponse.class
                    );
            PaymentHttpResponse body = resp.getBody();
            if (body == null) {
                throw new PakGoPayException(ResultCode.HTTP_REQUEST_ERROR);
            }
            return body;
        } catch (RestClientResponseException e) {
            log.error("restTemplate request failed, method={}, url={}, status={}, message={}",
                    method, url, e.getRawStatusCode(), e.getMessage(), e);
            throw new PakGoPayException(ResultCode.HTTP_REQUEST_ERROR);
        } catch (RestClientException e) {
            log.error("restTemplate request failed, method={}, url={}, message={}", method, url, e.getMessage(), e);
            throw new PakGoPayException(ResultCode.HTTP_REQUEST_ERROR);
        } catch (Exception e) {
            log.error("restTemplate request error, method={}, url={}, message={}", method, url, e.getMessage(), e);
            throw new PakGoPayException(ResultCode.HTTP_REQUEST_ERROR);
        }
    }

    public <T> T retry(Supplier<T> supplier, int times) {
        RuntimeException last = null;
        for (int i = 0; i < times; i++) {
            try {
                return supplier.get();
            } catch (RuntimeException e) {
                last = e;
            }
        }
        throw last;
    }
//
//    OrderDto dto = retry(() -> queryOrder(orderNo), 2);

}
