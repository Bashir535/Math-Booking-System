package com.example.bookingsystem.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "booking.demo-password")
public class DemoAccounts implements ApplicationRunner {

    private final JdbcTemplate jdbc;
    private final String password;

    public DemoAccounts(
        JdbcTemplate jdbc,
        @Value("${booking.demo-password}") String password
    ) {
        this.jdbc = jdbc;
        this.password = password;
    }

    public void run(ApplicationArguments args) {
        if (
            password.length() < 12 ||
            password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length >
                72
        ) throw new IllegalArgumentException(
            "Demo password must contain 12 to 72 characters"
        );
        var encoder = new BCryptPasswordEncoder();
        for (String name : new String[] {
            "alex.student",
            "maya.chen",
            "daniel.reyes",
        })
            jdbc.update(
                "UPDATE users SET password_hash=? WHERE username=?",
                encoder.encode(password),
                name
            );
    }
}
