package com.example.bookingsystem.notification;

import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class NotificationRepository {

    private final JdbcTemplate jdbc;

    public NotificationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void enqueue(long appointment, NotificationRequest.Type type) {
        jdbc.update(
            """
            INSERT INTO notification_outbox(id, appointment_id, recipient, type, message)
            SELECT ?, a.id, u.username, ?, ? FROM appointments a
            JOIN users u ON u.id=a.customer_id WHERE a.id=?
            ON CONFLICT(appointment_id, type) DO NOTHING
            """,
            UUID.randomUUID(),
            type.name(),
            "Your tutoring appointment " + appointment + " is " + type.name().toLowerCase() + ".",
            appointment
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<NotificationRequest> claim() {
        return jdbc
            .query(
                """
                UPDATE notification_outbox SET next_attempt_at=CURRENT_TIMESTAMP+INTERVAL '60 seconds',
                    delivery_rounds=delivery_rounds+1
                WHERE id=(SELECT id FROM notification_outbox
                    WHERE delivered_at IS NULL AND next_attempt_at<=CURRENT_TIMESTAMP
                    ORDER BY created_at, id FOR UPDATE SKIP LOCKED LIMIT 1)
                RETURNING id, recipient, type, message
                """,
                (r, n) ->
                    new NotificationRequest(
                        r.getObject("id", UUID.class),
                        r.getString("recipient"),
                        NotificationRequest.Type.valueOf(r.getString("type")),
                        r.getString("message")
                    )
            )
            .stream()
            .findFirst();
    }

    public void delivered(UUID id) {
        jdbc.update("UPDATE notification_outbox SET delivered_at=CURRENT_TIMESTAMP WHERE id=?", id);
    }

    public void defer(UUID id) {
        jdbc.update(
            "UPDATE notification_outbox SET next_attempt_at=CURRENT_TIMESTAMP+INTERVAL '30 seconds' WHERE id=?",
            id
        );
    }

    public boolean receive(NotificationRequest request) {
        return (
            jdbc.update(
                """
                INSERT INTO notification_receipts(id, recipient, type, message) VALUES(?,?,?,?)
                ON CONFLICT(id) DO NOTHING
                """,
                request.id(),
                request.recipient(),
                request.type().name(),
                request.message()
            ) == 1
        );
    }

    public boolean matches(NotificationRequest request) {
        return Boolean.TRUE.equals(
            jdbc.queryForObject(
                """
                SELECT EXISTS(SELECT 1 FROM notification_receipts
                WHERE id=? AND recipient=? AND type=? AND message=?)
                """,
                Boolean.class,
                request.id(),
                request.recipient(),
                request.type().name(),
                request.message()
            )
        );
    }
}
