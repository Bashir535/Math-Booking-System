package com.example.bookingsystem.controller;

import com.example.bookingsystem.dto.*;
import com.example.bookingsystem.service.BookingService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
public class BookingController {

    private final BookingService service;

    public BookingController(BookingService service) {
        this.service = service;
    }

    public record Booking(@Positive long slotId, @Positive long serviceId) {}

    public record Availability(
        @Positive long serviceId,
        @NotNull @Future OffsetDateTime startsAt
    ) {}

    private long user(HttpSession session) {
        return ((AccountDto) session.getAttribute("user")).id();
    }

    @PostMapping("/api/customer/appointments")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Long> book(
        @Valid @RequestBody Booking body,
        HttpSession session
    ) {
        return Map.of(
            "id",
            service.book(user(session), body.slotId(), body.serviceId())
        );
    }

    @DeleteMapping("/api/customer/appointments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable long id, HttpSession session) {
        service.cancel(user(session), id);
    }

    @GetMapping("/api/customer/appointments")
    public List<AppointmentDto> mine(HttpSession session) {
        return service.appointments(user(session), false);
    }

    @GetMapping("/api/provider/appointments")
    public List<AppointmentDto> provider(HttpSession session) {
        return service.appointments(user(session), true);
    }

    @PostMapping("/api/provider/slots")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Long> create(
        @Valid @RequestBody Availability body,
        HttpSession session
    ) {
        return Map.of(
            "id",
            service.create(user(session), body.serviceId(), body.startsAt())
        );
    }

    @DeleteMapping("/api/provider/slots/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable long id, HttpSession session) {
        service.remove(user(session), id);
    }
}
