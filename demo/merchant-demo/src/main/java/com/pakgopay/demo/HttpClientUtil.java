package com.pakgopay.demo;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class HttpClientUtil {
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public String postJson(String url, String token, String body) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body == null ? "{}" : body));
        if (token != null && !token.isBlank()) {
            builder.header("Authorization", token);
        }
        HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        return "HTTP " + response.statusCode() + "\n" + response.body();
    }
}
