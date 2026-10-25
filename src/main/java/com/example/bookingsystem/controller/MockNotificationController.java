package com.example.bookingsystem.controller;

import com.example.bookingsystem.notification.NotificationRepository;
import com.example.bookingsystem.notification.NotificationRequest;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class MockNotificationController {

    private final NotificationRepository repository;
    private final String key;
    private final boolean available;
    private final long delayMs;

    public MockNotificationController(
        NotificationRepository repository,
        @Value("${notification.api-key}") String key,
        @Value("${notification.stub.available:true}") boolean available,
        @Value("${notification.stub.delay-ms:0}") long delayMs
    ) {
        if (delayMs < 0 || delayMs > 10000) throw new IllegalArgumentException(
            "Stub delay must be 0 to 10000 ms"
        );
        this.repository = repository;
        this.key = key;
        this.available = available;
        this.delayMs = delayMs;
    }

    @PostMapping("/internal/notifications")
    public Map<String, String> receive(
        @RequestHeader(value = "X-Notification-Key", defaultValue = "") String supplied,
        @Valid @RequestBody NotificationRequest request
    ) throws InterruptedException {
        if (
            !MessageDigest.isEqual(
                key.getBytes(StandardCharsets.UTF_8),
                supplied.getBytes(StandardCharsets.UTF_8)
            )
        ) throw new ResponseStatusException(
            HttpStatus.FORBIDDEN,
            "Invalid notification credentials."
        );
        if (!available) throw new ResponseStatusException(
            HttpStatus.SERVICE_UNAVAILABLE,
            "Notification service unavailable."
        );
        if (delayMs > 0) Thread.sleep(delayMs);
        boolean inserted = repository.receive(request);
        if (!inserted && !repository.matches(request)) throw new ResponseStatusException(
            HttpStatus.CONFLICT,
            "Notification identifier already used."
        );
        if (inserted) LoggerFactory.getLogger(getClass()).info(
            "notification_received eventId={} type={}",
            request.id(),
            request.type()
        );
        return Map.of("status", inserted ? "accepted" : "duplicate");
    }
}
