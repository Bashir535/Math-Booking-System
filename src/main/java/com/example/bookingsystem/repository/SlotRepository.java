package com.example.bookingsystem.repository;

import com.example.bookingsystem.dto.SlotDto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
public class SlotRepository {
    private final JdbcTemplate jdbc;

    public SlotRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<SlotDto> findAvailable() {
        return jdbc.query("""
                SELECT sl.id, sl.provider_id, p.display_name AS provider_name,
                       sl.service_id, s.name AS service_name, sl.starts_at, sl.ends_at, s.price
                FROM availability_slots sl
                JOIN providers p ON p.id = sl.provider_id
                JOIN services s ON s.id = sl.service_id
                WHERE sl.starts_at > CURRENT_TIMESTAMP
                  AND NOT EXISTS (
                      SELECT 1 FROM appointments a
                      WHERE a.slot_id = sl.id AND a.status <> 'CANCELLED'
                  )
                ORDER BY sl.starts_at, sl.id
                """, (rs, row) -> new SlotDto(
                rs.getLong("id"), rs.getLong("provider_id"), rs.getString("provider_name"),
                rs.getLong("service_id"), rs.getString("service_name"),
                rs.getObject("starts_at", OffsetDateTime.class),
                rs.getObject("ends_at", OffsetDateTime.class), rs.getBigDecimal("price")));
    }
}
