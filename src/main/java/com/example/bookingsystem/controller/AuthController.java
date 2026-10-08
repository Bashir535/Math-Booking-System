package com.example.bookingsystem.controller;

import com.example.bookingsystem.dto.AccountDto;
import com.example.bookingsystem.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService service;

    public AuthController(AuthService service) {
        this.service = service;
    }

    public record Login(
        @NotBlank @Size(max = 80) String username,
        @NotBlank @Size(max = 72) String password
    ) {}

    @GetMapping("/session")
    public Map<String, Object> session(HttpServletRequest request) {
        var session = request.getSession();
        if (session.getAttribute("csrf") == null) session.setAttribute(
            "csrf",
            UUID.randomUUID().toString()
        );
        var user = session.getAttribute("user");
        return Map.of(
            "user",
            user == null ? Map.of() : user,
            "csrf",
            session.getAttribute("csrf")
        );
    }

    @PostMapping("/login")
    public AccountDto login(
        @Valid @RequestBody Login body,
        HttpServletRequest request
    ) {
        var user = service.login(body.username(), body.password());
        request.changeSessionId();
        request.getSession().setAttribute("user", user);
        return user;
    }

    @PostMapping("/logout")
    public void logout(HttpServletRequest request) {
        request.getSession().invalidate();
    }
}
