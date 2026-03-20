package com.pakgopay.thirdparty.demo;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

@RestController
public class MockThirdPartyController {

    private static final String AUTH_PREFIX = "api-key ";
    private static final String SIGN_ALGO = "HmacSHA1";

    // Demo merchant credentials (align with packgopay interfaceParam values in your local test data).
    private static final Map<String, MerchantCredential> MERCHANTS = new HashMap<>();

    // In-memory order storage for query endpoints.
    private static final Map<String, OrderRecord> ORDERS = new ConcurrentHashMap<>();
    private static final RestTemplate REST_TEMPLATE = new RestTemplate();

    static {
        MERCHANTS.put("24", new MerchantCredential("021fdff9059411f0", "75b7cb58f2f9fc7cf477172364c4ff39"));
        MERCHANTS.put("374", new MerchantCredential("374", "9a979c9975b056985cd7387604e7e23b"));
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> body = new HashMap<>();
        body.put("status", "UP");
        body.put("time", Instant.now().toString());
        return body;
    }

    @PostMapping("/api/v3/deposits")
    public ResponseEntity<Map<String, Object>> createDeposit(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody Map<String, Object> request) {
        return createOrder("deposit", authorization, request);
    }

    @PostMapping("/api/v3/transfers")
    public ResponseEntity<Map<String, Object>> createTransfer(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody Map<String, Object> request) {
        return createOrder("transfer", authorization, request);
    }

    @PostMapping("/api/v3/deposits/query")
    public ResponseEntity<Map<String, Object>> queryDeposit(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody Map<String, Object> request) {
        return queryOrder("deposit", authorization, request);
    }

    @PostMapping("/api/v3/transfers/query")
    public ResponseEntity<Map<String, Object>> queryTransfer(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody Map<String, Object> request) {
        return queryOrder("transfer", authorization, request);
    }

    @PostMapping("/api/v3/balance")
    public ResponseEntity<Map<String, Object>> balance(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody Map<String, Object> request) {
        ValidationResult valid = validateSignAndAuth(authorization, request);
        if (!valid.ok) {
            return ResponseEntity.ok(error(valid.message));
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("balance", "500000.00");
        data.put("available_balance", "500000.00");
        return ResponseEntity.ok(success(data));
    }

    private ResponseEntity<Map<String, Object>> createOrder(
            String type,
            String authorization,
            Map<String, Object> request) {
        ValidationResult valid = validateSignAndAuth(authorization, request);
        if (!valid.ok) {
            return ResponseEntity.ok(error(valid.message));
        }

        String orderNo = stringValue(request.get("order_no"));
        String amount = stringValue(request.get("amount"));
        String notifyUrl = stringValue(request.get("notify_url"));
        if (isBlank(orderNo) || isBlank(amount) || isBlank(notifyUrl)) {
            return ResponseEntity.ok(error("missing order_no or amount or notify_url"));
        }
        if (!ThreadLocalRandom.current().nextBoolean()) {
            return ResponseEntity.ok(error("测试失败"));
        }

        OrderRecord record = new OrderRecord();
        record.type = type;
        record.orderNo = orderNo;
        record.amount = amount;
        record.status = "succeeded"; // Test handler default: succeed directly.
        record.createdAt = Instant.now().getEpochSecond();
        record.merchantId = stringValue(request.get("mid"));
        record.notifyUrl = notifyUrl;
        ORDERS.put(orderNo, record);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("mid", toInt(request.get("mid")));
        data.put("order_no", orderNo);
        data.put("no", "D" + System.currentTimeMillis());
        data.put("amount", amount);
        data.put("actual_amount", amount);
        data.put("fee", "0.00");
        data.put("created_time", record.createdAt);
        data.put("deposit_time", record.createdAt);
        data.put("notify_time", record.createdAt);
        data.put("status", record.status);
        data.put("url", "https://mock-third-party.local/cashier?no=" + data.get("no"));

        sendNotifyAsync(record);
        return ResponseEntity.ok(success(data));
    }

    private ResponseEntity<Map<String, Object>> queryOrder(
            String type,
            String authorization,
            Map<String, Object> request) {
        ValidationResult valid = validateSignAndAuth(authorization, request);
        if (!valid.ok) {
            return ResponseEntity.ok(error(valid.message));
        }

        String orderNo = stringValue(request.get("order_no"));
        OrderRecord record = ORDERS.get(orderNo);
        if (record == null || !Objects.equals(record.type, type)) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("order_no", orderNo);
            data.put("status", "failed");
            return ResponseEntity.ok(success(data));
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("order_no", record.orderNo);
        data.put("amount", record.amount);
        data.put("actual_amount", record.amount);
        data.put("status", record.status);
        data.put("created_time", record.createdAt);
        return ResponseEntity.ok(success(data));
    }

    private ValidationResult validateSignAndAuth(String authorization, Map<String, Object> request) {
        String mid = stringValue(request.get("mid"));
        if (isBlank(mid)) {
            return ValidationResult.fail("missing mid");
        }
        MerchantCredential credential = MERCHANTS.get(mid);
        if (credential == null) {
            return ValidationResult.fail("unknown mid");
        }

        String expectedAuth = AUTH_PREFIX + credential.apiKey;
        if (!expectedAuth.equals(authorization)) {
            return ValidationResult.fail("authorization error");
        }

        String sign = stringValue(request.get("sign"));
        if (isBlank(sign)) {
            return ValidationResult.fail("missing sign");
        }

        String calculated = signSha1Base64(request, credential.signKey);
        if (!Objects.equals(sign, calculated)) {
            return ValidationResult.fail("signature error");
        }

        return ValidationResult.ok();
    }

    private void sendNotifyAsync(OrderRecord record) {
        if (record == null || isBlank(record.notifyUrl) || isBlank(record.merchantId)) {
            return;
        }
        MerchantCredential credential = MERCHANTS.get(record.merchantId);
        if (credential == null) {
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(1000L);
                long now = Instant.now().getEpochSecond();
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("mid", toInt(record.merchantId));
                body.put("no", "D" + System.currentTimeMillis());
                body.put("order_no", record.orderNo);
                body.put("amount", record.amount);
                body.put("actual_amount", record.amount);
                body.put("fee", "0.00");
                body.put("created_time", record.createdAt);
                body.put("deposit_time", now);
                body.put("notify_time", now);
                body.put("status", "succeeded");
                body.put("orig_amount", record.amount);
                body.put("payer_name", "demo");
                body.put("sign", signSha1Base64(body, credential.signKey));

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                REST_TEMPLATE.postForEntity(record.notifyUrl, new HttpEntity<>(body, headers), String.class);
            } catch (Exception ignored) {
                // Ignore callback failure for mock service.
            }
        });
    }

    private String signSha1Base64(Map<String, Object> params, String signKey) {
        try {
            List<Map.Entry<String, Object>> entries = new ArrayList<>(params.entrySet());
            entries.removeIf(e -> e.getKey() == null || e.getValue() == null || isBlank(stringValue(e.getValue())) || "sign".equals(e.getKey()));
            entries.sort(Comparator.comparing(Map.Entry::getKey));

            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < entries.size(); i++) {
                Map.Entry<String, Object> entry = entries.get(i);
                if (i > 0) {
                    sb.append('&');
                }
                sb.append(entry.getKey()).append('=').append(entry.getValue());
            }

            Mac mac = Mac.getInstance(SIGN_ALGO);
            mac.init(new SecretKeySpec(signKey.getBytes(StandardCharsets.UTF_8), SIGN_ALGO));
            byte[] digest = mac.doFinal(sb.toString().getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (Exception e) {
            throw new IllegalStateException("sign failed", e);
        }
    }

    private Map<String, Object> success(Map<String, Object> data) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", 200);
        body.put("message", "OK");
        body.put("data", data);
        return body;
    }

    private Map<String, Object> error(String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", -9999);
        body.put("message", message);
        body.put("data", null);
        return body;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }

    private Integer toInt(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(String.valueOf(value)).intValue();
        } catch (Exception e) {
            return null;
        }
    }

    private static class MerchantCredential {
        private final String apiKey;
        private final String signKey;

        private MerchantCredential(String apiKey, String signKey) {
            this.apiKey = apiKey;
            this.signKey = signKey;
        }
    }

    private static class OrderRecord {
        private String type;
        private String orderNo;
        private String amount;
        private String status;
        private long createdAt;
        private String merchantId;
        private String notifyUrl;
    }

    private static class ValidationResult {
        private final boolean ok;
        private final String message;

        private ValidationResult(boolean ok, String message) {
            this.ok = ok;
            this.message = message;
        }

        private static ValidationResult ok() {
            return new ValidationResult(true, null);
        }

        private static ValidationResult fail(String message) {
            return new ValidationResult(false, message);
        }
    }
}
