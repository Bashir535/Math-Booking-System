package com.example.bookingsystem.repository;

import com.example.bookingsystem.dto.SlotDto;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SlotRepository {

    private final JdbcTemplate jdbc;

    public SlotRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<SlotDto> findAvailable() {
        return search(null, null, null, 0, 100);
    }

    public List<SlotDto> search(
        Long provider,
        Long service,
        LocalDate date,
        int page,
        int size
    ) {
        StringBuilder sql = new StringBuilder(
            """
            SELECT sl.id,sl.provider_id,p.display_name,sl.service_id,s.name,sl.starts_at,sl.ends_at,s.price
            FROM availability_slots sl JOIN providers p ON p.id=sl.provider_id JOIN services s ON s.id=sl.service_id
            WHERE NOT sl.removed AND sl.starts_at>CURRENT_TIMESTAMP
            AND NOT EXISTS(SELECT 1 FROM appointments a WHERE a.slot_id=sl.id AND a.status<>'CANCELLED')
            """
        );
        List<Object> args = new ArrayList<>();
        if (provider != null) {
            sql.append(" AND sl.provider_id=?");
            args.add(provider);
        }
        if (service != null) {
            sql.append(" AND sl.service_id=?");
            args.add(service);
        }
        if (date != null) {
            var zone = ZoneId.of("America/Los_Angeles");
            sql.append(" AND sl.starts_at>=? AND sl.starts_at<?");
            args.add(date.atStartOfDay(zone).toOffsetDateTime());
            args.add(date.plusDays(1).atStartOfDay(zone).toOffsetDateTime());
        }
        sql.append(" ORDER BY sl.starts_at,sl.id LIMIT ? OFFSET ?");
        args.add(size);
        args.add((long) page * size);
        return jdbc.query(
            sql.toString(),
            (r, n) ->
                new SlotDto(
                    r.getLong(1),
                    r.getLong(2),
                    r.getString(3),
                    r.getLong(4),
                    r.getString(5),
                    r.getObject(6, OffsetDateTime.class),
                    r.getObject(7, OffsetDateTime.class),
                    r.getBigDecimal(8)
                ),
            args.toArray()
        );
    }
}
