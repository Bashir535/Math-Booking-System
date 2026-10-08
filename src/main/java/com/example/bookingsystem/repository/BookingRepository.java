package com.example.bookingsystem.repository;

import com.example.bookingsystem.dto.AppointmentDto;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class BookingRepository {

    private final JdbcTemplate jdbc;

    public BookingRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record LockedSlot(
        long id,
        long serviceId,
        long providerUserId,
        OffsetDateTime startsAt,
        boolean removed
    ) {}

    public record Appointment(
        long id,
        long customerId,
        long slotId,
        String status
    ) {}

    public Optional<LockedSlot> lockSlot(long id) {
        return jdbc
            .query(
                """
                SELECT sl.*, p.user_id FROM availability_slots sl JOIN providers p ON p.id=sl.provider_id
                WHERE sl.id=? FOR UPDATE OF sl
                """,
                (r, n) ->
                    new LockedSlot(
                        r.getLong("id"),
                        r.getLong("service_id"),
                        r.getLong("user_id"),
                        r.getObject("starts_at", OffsetDateTime.class),
                        r.getBoolean("removed")
                    ),
                id
            )
            .stream()
            .findFirst();
    }

    public boolean occupied(long slot) {
        return Boolean.TRUE.equals(
            jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM appointments WHERE slot_id=? AND status<>'CANCELLED')",
                Boolean.class,
                slot
            )
        );
    }

    public long insert(long customer, long slot, long service) {
        return jdbc.queryForObject(
            "INSERT INTO appointments(customer_id,slot_id,service_id) VALUES(?,?,?) RETURNING id",
            Long.class,
            customer,
            slot,
            service
        );
    }

    public Optional<Appointment> find(long id) {
        return jdbc
            .query(
                "SELECT id,customer_id,slot_id,status FROM appointments WHERE id=?",
                (r, n) ->
                    new Appointment(
                        r.getLong(1),
                        r.getLong(2),
                        r.getLong(3),
                        r.getString(4)
                    ),
                id
            )
            .stream()
            .findFirst();
    }

    public void cancel(long id) {
        jdbc.update(
            "UPDATE appointments SET status='CANCELLED',cancelled_at=CURRENT_TIMESTAMP WHERE id=?",
            id
        );
    }

    public void remove(long id) {
        jdbc.update(
            "UPDATE availability_slots SET removed=true WHERE id=?",
            id
        );
    }

    public Optional<Long> provider(long user) {
        return jdbc
            .query(
                "SELECT id FROM providers WHERE user_id=?",
                (r, n) -> r.getLong(1),
                user
            )
            .stream()
            .findFirst();
    }

    public Optional<Integer> duration(long service) {
        return jdbc
            .query(
                "SELECT duration_minutes FROM services WHERE id=?",
                (r, n) -> r.getInt(1),
                service
            )
            .stream()
            .findFirst();
    }

    public long create(
        long provider,
        long service,
        OffsetDateTime start,
        int duration
    ) {
        return jdbc.queryForObject(
            "INSERT INTO availability_slots(provider_id,service_id,starts_at,ends_at) VALUES(?,?,?,?) RETURNING id",
            Long.class,
            provider,
            service,
            start,
            start.plusMinutes(duration)
        );
    }

    public List<AppointmentDto> appointments(long user, boolean provider) {
        String owner = provider ? "p.user_id" : "a.customer_id";
        jdbc.update(
            """
            UPDATE appointments a SET status='COMPLETED' FROM availability_slots sl, providers p
            WHERE a.slot_id=sl.id AND sl.provider_id=p.id AND a.status='BOOKED' AND sl.ends_at<=CURRENT_TIMESTAMP AND
            """ +
                owner +
                "=?",
            user
        );
        return jdbc.query(
            """
            SELECT a.id,a.slot_id,u.full_name,p.display_name,s.name,sl.starts_at,sl.ends_at,a.status
            FROM appointments a JOIN users u ON u.id=a.customer_id
            JOIN availability_slots sl ON sl.id=a.slot_id JOIN providers p ON p.id=sl.provider_id
            JOIN services s ON s.id=a.service_id WHERE
            """ +
                owner +
                "=? ORDER BY sl.starts_at DESC,a.id DESC",
            (r, n) ->
                new AppointmentDto(
                    r.getLong(1),
                    r.getLong(2),
                    r.getString(3),
                    r.getString(4),
                    r.getString(5),
                    r.getObject(6, OffsetDateTime.class),
                    r.getObject(7, OffsetDateTime.class),
                    r.getString(8)
                ),
            user
        );
    }
}
