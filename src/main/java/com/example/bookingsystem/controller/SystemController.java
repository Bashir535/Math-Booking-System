package com.example.bookingsystem.controller;

import java.util.Map;
import javax.sql.DataSource;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
public class SystemController {

    private final JdbcTemplate jdbc;

    public SystemController(DataSource source) {
        this.jdbc = new JdbcTemplate(source);
        this.jdbc.setQueryTimeout(2);
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        try {
            jdbc.queryForObject("SELECT 1", Integer.class);
            return ResponseEntity.ok(Map.of("status", "UP", "database", "UP"));
        } catch (DataAccessException e) {
            LoggerFactory.getLogger(getClass()).error(
                "health_database_down reason={}",
                e.getClass().getSimpleName()
            );
            return ResponseEntity.status(503).body(Map.of("status", "DOWN", "database", "DOWN"));
        }
    }

    @GetMapping("/metrics")
    public Map<String, Object> metrics() {
        return jdbc.queryForMap(
            """
            SELECT (SELECT count(*) FROM appointments) AS bookings_created_total,
                (SELECT count(*) FROM appointments WHERE status='CANCELLED') AS bookings_cancelled_total,
                (SELECT count(*) FROM notification_outbox WHERE delivered_at IS NULL) AS notifications_pending,
                (SELECT count(*) FROM notification_outbox WHERE delivered_at IS NOT NULL) AS notifications_delivered_total
            """
        );
    }
}
