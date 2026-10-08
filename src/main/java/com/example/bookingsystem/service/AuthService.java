package com.example.bookingsystem.service;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import com.example.bookingsystem.dto.AccountDto;
import com.example.bookingsystem.repository.AccountRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

    private final AccountRepository repository;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final String dummy = encoder.encode("unavailable account");

    public AuthService(AccountRepository repository) {
        this.repository = repository;
    }

    public AccountDto login(String username, String password) {
        if (
            password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length >
            72
        ) {
            throw new ResponseStatusException(
                UNAUTHORIZED,
                "Invalid username or password."
            );
        }
        var credentials = repository.find(username);
        boolean valid = encoder.matches(
            password,
            credentials.map(AccountRepository.Credentials::hash).orElse(dummy)
        );
        if (credentials.isEmpty() || !valid) throw new ResponseStatusException(
            UNAUTHORIZED,
            "Invalid username or password."
        );
        return credentials.get().account();
    }
}
