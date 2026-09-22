package com.example.bookingsystem.repository;

import com.example.bookingsystem.dto.ProviderDto;
import com.example.bookingsystem.dto.TutoringServiceDto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class CatalogRepository {
    private final JdbcTemplate jdbc;

    public CatalogRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<ProviderDto> findProviders() {
        return jdbc.query("""
                SELECT id, display_name, bio
                FROM providers
                ORDER BY display_name, id
                """, (rs, row) -> new ProviderDto(
                rs.getLong("id"), rs.getString("display_name"), rs.getString("bio")));
    }

    public List<TutoringServiceDto> findServices() {
        return jdbc.query("""
                SELECT id, name, description, duration_minutes, price
                FROM services
                ORDER BY name, id
                """, (rs, row) -> new TutoringServiceDto(
                rs.getLong("id"), rs.getString("name"), rs.getString("description"),
                rs.getInt("duration_minutes"), rs.getBigDecimal("price")));
    }
}
