package com.fyp.floodmonitoring.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Direct Resend HTTP API client. Bypasses Spring Mail SMTP because the SMTP
 * relay through smtp.resend.com:465 was silently failing on Railway while the
 * HTTP API path consistently delivers.
 */
@Slf4j
@Component
public class ResendHttpClient {

    private static final String ENDPOINT = "https://api.resend.com/emails";

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ObjectMapper json = new ObjectMapper();

    @Value("${app.email.resend-api-key:}")
    private String apiKey;

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    public boolean sendText(String fromHeader, String to, String subject, String text) {
        return send(fromHeader, to, subject, "text", text);
    }

    public boolean sendHtml(String fromHeader, String to, String subject, String html) {
        return send(fromHeader, to, subject, "html", html);
    }

    private boolean send(String fromHeader, String to, String subject, String bodyKey, String bodyValue) {
        if (!isConfigured()) {
            log.warn("[Email] RESEND_API_KEY is blank — email NOT sent. Set RESEND_API_KEY env var. To={} Subject='{}'",
                    to, subject);
            return false;
        }
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("from", fromHeader);
            payload.put("to", new String[] { to });
            payload.put("subject", subject);
            payload.put(bodyKey, bodyValue);

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(ENDPOINT))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(payload)))
                    .build();

            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() >= 200 && res.statusCode() < 300) {
                log.info("[Email] Sent via Resend HTTP to={} subject='{}' status={}", to, subject, res.statusCode());
                return true;
            }
            log.error("[Email] Resend HTTP rejected to={} subject='{}' status={} body={}",
                    to, subject, res.statusCode(), res.body());
            return false;
        } catch (Exception e) {
            log.error("[Email] Resend HTTP failed to={} subject='{}': {}", to, subject, e.toString());
            return false;
        }
    }
}
