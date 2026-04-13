package com.pakgopay.thirdparty.demo;

import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.pool.PoolConcurrencyPolicy;
import org.apache.hc.core5.pool.PoolReusePolicy;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PreDestroy;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.net.InetAddress;
import java.net.URI;
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
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@RestController
public class MockThirdPartyController {

    private static final Logger log = LoggerFactory.getLogger(MockThirdPartyController.class);

    private static final String AUTH_PREFIX = "api-key ";
    private static final String SIGN_ALGO = "HmacSHA1";
    private static final int NOTIFY_MAX_ATTEMPTS = 5;
    private static final long NOTIFY_INITIAL_DELAY_MS = 1000L;
    private static final long[] NOTIFY_RETRY_BACKOFF_MS = {1000L, 5000L, 15000L, 30000L, 60000L};
    private static final int NOTIFY_WORKER_THREADS = 48;
    private static final int NOTIFY_QUEUE_CAPACITY = 10000;
    private static final int NOTIFY_POOL_MAX_TOTAL = 600;
    private static final int NOTIFY_POOL_MAX_PER_ROUTE = 400;
    private static final int NOTIFY_CONNECT_TIMEOUT_MS = 3000;
    private static final int NOTIFY_CONNECTION_REQUEST_TIMEOUT_MS = 3000;
    private static final int NOTIFY_READ_TIMEOUT_MS = 30000;
    private static final TrackingHttpComponentsClientHttpRequestFactory HTTP_REQUEST_FACTORY =
            new TrackingHttpComponentsClientHttpRequestFactory(buildNotifyHttpClient());
    private static final RestTemplate REST_TEMPLATE = new RestTemplate(HTTP_REQUEST_FACTORY);

    // Demo merchant credentials (align with packgopay interfaceParam values in your local test data).
    private static final Map<String, MerchantCredential> MERCHANTS = new HashMap<>();

    // In-memory order storage for query endpoints.
    private static final Map<String, OrderRecord> ORDERS = new ConcurrentHashMap<>();

    private final ThreadPoolExecutor notifyExecutor = new ThreadPoolExecutor(
            NOTIFY_WORKER_THREADS,
            NOTIFY_WORKER_THREADS,
            0L,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(NOTIFY_QUEUE_CAPACITY),
            namedThreadFactory("mock-notify-worker"),
            new ThreadPoolExecutor.CallerRunsPolicy());
    private final ScheduledExecutorService notifyRetryScheduler =
            Executors.newSingleThreadScheduledExecutor(namedThreadFactory("mock-notify-retry"));

    static {
        MERCHANTS.put("24", new MerchantCredential("021fdff9059411f0", "75b7cb58f2f9fc7cf477172364c4ff39"));
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
//        if (!ThreadLocalRandom.current().nextBoolean()) {
//            return ResponseEntity.ok(error("测试失败"));
//        }

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

        scheduleNotify(record, 1, NOTIFY_INITIAL_DELAY_MS);
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

    private void scheduleNotify(OrderRecord record, int attempt, long delayMs) {
        if (record == null || isBlank(record.notifyUrl) || isBlank(record.merchantId)) {
            return;
        }
        MerchantCredential credential = MERCHANTS.get(record.merchantId);
        if (credential == null) {
            return;
        }
        notifyRetryScheduler.schedule(() -> submitNotify(new NotifyTask(record, credential, attempt)),
                Math.max(delayMs, 0L), TimeUnit.MILLISECONDS);
    }

    private void submitNotify(NotifyTask task) {
        int queuedBeforeSubmit = notifyExecutor.getQueue().size();
        if (queuedBeforeSubmit >= NOTIFY_QUEUE_CAPACITY) {
            log.warn("mock notify queue saturated, caller-runs fallback, attempt={}/{}, orderNo={}, notifyUrl={}, queued={}, active={}",
                    task.attempt, NOTIFY_MAX_ATTEMPTS, task.record.orderNo, task.record.notifyUrl,
                    queuedBeforeSubmit, notifyExecutor.getActiveCount());
        }
        notifyExecutor.execute(() -> processNotify(task));
    }

    private void processNotify(NotifyTask task) {
        try {
            long now = Instant.now().getEpochSecond();
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("mid", toInt(task.record.merchantId));
            body.put("no", "D" + System.currentTimeMillis());
            body.put("order_no", task.record.orderNo);
            body.put("amount", task.record.amount);
            body.put("actual_amount", task.record.amount);
            body.put("fee", "0.00");
            body.put("created_time", task.record.createdAt);
            body.put("deposit_time", now);
            body.put("notify_time", now);
            body.put("status", "succeeded");
            body.put("orig_amount", task.record.amount);
            body.put("payer_name", "demo");
            body.put("sign", signSha1Base64(body, task.credential.signKey));

            Map<String, Object> notifyPayload = new LinkedHashMap<>();
            notifyPayload.put("code", 200);
            notifyPayload.put("message", "OK");
            notifyPayload.put("data", body);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.add("X-Order-No", task.record.orderNo);
            headers.add("X-Notify-Type", "thirdparty-demo");

            String resolvedIps = resolveHostIps(task.record.notifyUrl);
            REST_TEMPLATE.postForEntity(task.record.notifyUrl, new HttpEntity<>(notifyPayload, headers), String.class);
        } catch (Exception e) {
            String connectTarget = HTTP_REQUEST_FACTORY.consumeLastRemote();
            String resolvedIps = resolveHostIps(task.record.notifyUrl);
            log.error("mock notify attempt failed, attempt={}/{}, orderNo={}, notifyUrl={}, resolvedIps={}, connectTarget={}, message={}",
                    task.attempt, NOTIFY_MAX_ATTEMPTS, task.record.orderNo, task.record.notifyUrl,
                    resolvedIps, connectTarget, e.getMessage());
            scheduleRetry(task, e.getMessage(), e);
        }
    }

    private void scheduleRetry(NotifyTask task, String lastMessage, Exception lastException) {
        if (task.attempt >= NOTIFY_MAX_ATTEMPTS) {
            log.error("mock notify failed after {} attempts, orderNo={}, notifyUrl={}, message={}",
                    NOTIFY_MAX_ATTEMPTS, task.record.orderNo, task.record.notifyUrl, lastMessage, lastException);
            return;
        }
        long delayMs = NOTIFY_RETRY_BACKOFF_MS[Math.min(task.attempt, NOTIFY_RETRY_BACKOFF_MS.length - 1)];
        scheduleNotify(task.record, task.attempt + 1, delayMs);
    }

    @PreDestroy
    public void shutdownExecutors() {
        notifyRetryScheduler.shutdown();
        notifyExecutor.shutdown();
    }

    private static ThreadFactory namedThreadFactory(String prefix) {
        return runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName(prefix + "-" + thread.threadId());
            thread.setDaemon(true);
            return thread;
        };
    }

    private String resolveHostIps(String notifyUrl) {
        try {
            URI uri = URI.create(notifyUrl);
            String host = uri.getHost();
            if (isBlank(host)) {
                return "host_empty";
            }
            InetAddress[] addresses = InetAddress.getAllByName(host);
            List<String> parts = new ArrayList<>();
            for (InetAddress address : addresses) {
                String type = address.getAddress().length == 4 ? "IPv4" : "IPv6";
                parts.add(type + ":" + address.getHostAddress());
            }
            return host + " -> " + String.join(",", parts);
        } catch (Exception e) {
            return "resolve_failed:" + e.getMessage();
        }
    }

    private static HttpClient buildNotifyHttpClient() {
        Ipv4OnlyDnsResolver dnsResolver = new Ipv4OnlyDnsResolver();
        PoolingHttpClientConnectionManager connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
                .setDnsResolver(dnsResolver)
                .setPoolConcurrencyPolicy(PoolConcurrencyPolicy.STRICT)
                .setConnPoolPolicy(PoolReusePolicy.FIFO)
                .setMaxConnTotal(NOTIFY_POOL_MAX_TOTAL)
                .setMaxConnPerRoute(NOTIFY_POOL_MAX_PER_ROUTE)
                .setDefaultConnectionConfig(ConnectionConfig.custom()
                        .setConnectTimeout(Timeout.ofMilliseconds(NOTIFY_CONNECT_TIMEOUT_MS))
                        .setSocketTimeout(Timeout.ofMilliseconds(NOTIFY_READ_TIMEOUT_MS))
                        .setTimeToLive(TimeValue.ofSeconds(60))
                        .build())
                .setValidateAfterInactivity(TimeValue.ofSeconds(1))
                .build();

        return HttpClients.custom()
                .setConnectionManager(connectionManager)
                .evictExpiredConnections()
                .evictIdleConnections(TimeValue.ofSeconds(10))
                .disableAutomaticRetries()
                .build();
    }

    private static final class Ipv4OnlyDnsResolver implements DnsResolver {
        private static final ThreadLocal<String> LAST_REMOTE = new ThreadLocal<>();

        @Override
        public InetAddress[] resolve(String host) throws java.net.UnknownHostException {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            List<InetAddress> ipv4 = new ArrayList<>();
            for (InetAddress address : addresses) {
                if (address.getAddress().length == 4) {
                    ipv4.add(address);
                }
            }
            if (!ipv4.isEmpty()) {
                InetAddress first = ipv4.get(0);
                LAST_REMOTE.set("IPv4:" + first.getHostAddress() + ":443");
                return ipv4.toArray(new InetAddress[0]);
            }
            LAST_REMOTE.set("remote_not_recorded");
            return addresses;
        }

        @Override
        public String resolveCanonicalHostname(String host) throws java.net.UnknownHostException {
            return host;
        }

        static String consumeLastRemote() {
            String value = LAST_REMOTE.get();
            if (value == null) {
                return "remote_not_recorded";
            }
            LAST_REMOTE.remove();
            return value;
        }
    }

    private static final class TrackingHttpComponentsClientHttpRequestFactory extends HttpComponentsClientHttpRequestFactory {
        TrackingHttpComponentsClientHttpRequestFactory(HttpClient httpClient) {
            super(httpClient);
            setConnectTimeout(NOTIFY_CONNECT_TIMEOUT_MS);
            setConnectionRequestTimeout(NOTIFY_CONNECTION_REQUEST_TIMEOUT_MS);
            setReadTimeout(NOTIFY_READ_TIMEOUT_MS);
        }

        String consumeLastRemote() {
            return Ipv4OnlyDnsResolver.consumeLastRemote();
        }
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

    private static class NotifyTask {
        private final OrderRecord record;
        private final MerchantCredential credential;
        private final int attempt;

        private NotifyTask(OrderRecord record, MerchantCredential credential, int attempt) {
            this.record = record;
            this.credential = credential;
            this.attempt = attempt;
        }
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
