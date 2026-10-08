package com.example.bookingsystem.repository;

import com.example.bookingsystem.dto.AccountDto;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AccountRepository {

    public record Credentials(AccountDto account, String hash) {}

    private final JdbcTemplate jdbc;

    public AccountRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Credentials> find(String username) {
        return jdbc
            .query(
                "SELECT u.id, username, full_name, role, password_hash, p.id AS provider_id FROM users u LEFT JOIN providers p ON p.user_id=u.id WHERE username = ?",
                (rs, n) ->
                    new Credentials(
                        new AccountDto(
                            rs.getLong("id"),
                            rs.getString("username"),
                            rs.getString("full_name"),
                            rs.getString("role"),
                            rs.getObject("provider_id", Long.class)
                        ),
                        rs.getString("password_hash")
                    ),
                username
            )
            .stream()
            .findFirst();
    }
}
