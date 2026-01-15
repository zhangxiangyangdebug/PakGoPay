package com.pakgopay.common.http;

import com.pakgopay.data.response.http.PaymentHttpResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.function.Supplier;

@Service
public class RestTemplateUtil {

    @Autowired
    private RestTemplate restTemplate;

    public PaymentHttpResponse request(HttpEntity entity, HttpMethod method, String url) {

        ResponseEntity<PaymentHttpResponse> resp =
                restTemplate.exchange(
                        url,
                        method,
                        entity,
                        PaymentHttpResponse.class
                );

        return resp.getBody();
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
