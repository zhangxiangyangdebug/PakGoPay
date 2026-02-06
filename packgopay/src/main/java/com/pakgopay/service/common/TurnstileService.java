package com.pakgopay.service.common;

import com.pakgopay.data.response.TurnstileVerifyResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

@Service
public class TurnstileService {

    private final RestTemplate restTemplate;

    @Value("${turnstile.enabled:true}")
    private boolean enabled;

    @Value("${turnstile.secret:}")
    private String secret;

    @Value("${turnstile.verify-url:https://challenges.cloudflare.com/turnstile/v0/siteverify}")
    private String verifyUrl;

    public TurnstileService(RestTemplateBuilder builder) {
        this.restTemplate = builder.build();
    }

    public boolean verify(String token, String remoteIp) {
        if (!enabled) {
            return true;
        }
        if (token == null || token.isBlank() || secret == null || secret.isBlank()) {
            return false;
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("secret", secret);
        form.add("response", token);
        if (remoteIp != null && !remoteIp.isBlank()) {
            form.add("remoteip", remoteIp);
        }
        TurnstileVerifyResponse response = restTemplate.postForObject(verifyUrl, form, TurnstileVerifyResponse.class);
        return response != null && response.isSuccess();
    }
}
