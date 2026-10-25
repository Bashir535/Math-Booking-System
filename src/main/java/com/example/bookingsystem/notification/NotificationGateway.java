package com.example.bookingsystem.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class NotificationGateway {

    private static final Logger log = LoggerFactory.getLogger(NotificationGateway.class);
    private final HttpClient client;
    private final ObjectMapper json;
    private final URI endpoint;
    private final String key;
    private final int timeoutMs;

    public NotificationGateway(
        ObjectMapper json,
        @Value("${notification.url}") String url,
        @Value("${notification.api-key}") String key,
        @Value("${notification.timeout-ms:1000}") int timeoutMs
    ) {
        if (timeoutMs < 50 || timeoutMs > 5000) throw new IllegalArgumentException(
            "Notification timeout must be 50 to 5000 ms"
        );
        this.json = json;
        this.endpoint = URI.create(url);
        this.key = key;
        this.timeoutMs = timeoutMs;
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(timeoutMs)).build();
    }

    public boolean send(NotificationRequest request) {
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                var http = HttpRequest.newBuilder(endpoint)
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("Content-Type", "application/json")
                    .header("X-Notification-Key", key)
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(request)))
                    .build();
                int status = client.send(http, HttpResponse.BodyHandlers.discarding()).statusCode();
                if (status >= 200 && status < 300) return true;
                log.warn(
                    "notification_attempt_failed eventId={} attempt={} status={}",
                    request.id(),
                    attempt,
                    status
                );
                if (status >= 400 && status < 500 && status != 429) break;
            } catch (IOException e) {
                log.warn(
                    "notification_attempt_failed eventId={} attempt={} reason={}",
                    request.id(),
                    attempt,
                    e.getClass().getSimpleName()
                );
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
            if (attempt < 3) {
                try {
                    Thread.sleep(100L * attempt);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        return false;
    }
}
