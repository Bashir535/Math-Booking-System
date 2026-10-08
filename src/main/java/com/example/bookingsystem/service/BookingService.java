package com.example.bookingsystem.service;

import static org.springframework.http.HttpStatus.*;

import com.example.bookingsystem.dto.AppointmentDto;
import com.example.bookingsystem.repository.BookingRepository;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(isolation = Isolation.READ_COMMITTED, timeout = 10)
public class BookingService {

    private final BookingRepository repository;

    public BookingService(BookingRepository repository) {
        this.repository = repository;
    }

    public long book(long customer, long slotId, long serviceId) {
        if (slotId <= 0 || serviceId <= 0) throw new ResponseStatusException(
            BAD_REQUEST,
            "Choose a slot and subject."
        );
        var slot = repository
            .lockSlot(slotId)
            .orElseThrow(() ->
                new ResponseStatusException(NOT_FOUND, "Slot not found.")
            );
        if (slot.serviceId() != serviceId) throw new ResponseStatusException(
            BAD_REQUEST,
            "The subject does not match this slot."
        );
        if (
            slot.removed() ||
            !slot.startsAt().isAfter(OffsetDateTime.now()) ||
            repository.occupied(slotId)
        ) throw new ResponseStatusException(
            CONFLICT,
            "This slot is no longer available."
        );
        return repository.insert(customer, slotId, serviceId);
    }

    public void cancel(long customer, long id) {
        var appointment = repository
            .find(id)
            .orElseThrow(() ->
                new ResponseStatusException(NOT_FOUND, "Appointment not found.")
            );
        if (
            appointment.customerId() != customer
        ) throw new ResponseStatusException(
            FORBIDDEN,
            "You can only cancel your own appointments."
        );
        var slot = repository
            .lockSlot(appointment.slotId())
            .orElseThrow(() ->
                new ResponseStatusException(NOT_FOUND, "Slot not found.")
            );
        appointment = repository.find(id).orElseThrow();
        if (appointment.status().equals("CANCELLED")) return;
        if (
            !appointment.status().equals("BOOKED") ||
            !slot.startsAt().isAfter(OffsetDateTime.now())
        ) throw new ResponseStatusException(
            CONFLICT,
            "Only future appointments can be cancelled."
        );
        repository.cancel(id);
    }

    public long create(long user, long service, OffsetDateTime start) {
        if (
            service <= 0 ||
            start == null ||
            !start.isAfter(OffsetDateTime.now())
        ) throw new ResponseStatusException(
            BAD_REQUEST,
            "Choose a subject and a future time."
        );
        long provider = repository
            .provider(user)
            .orElseThrow(() ->
                new ResponseStatusException(
                    FORBIDDEN,
                    "Tutor profile not found."
                )
            );
        int duration = repository
            .duration(service)
            .orElseThrow(() ->
                new ResponseStatusException(NOT_FOUND, "Subject not found.")
            );
        return repository.create(provider, service, start, duration);
    }

    public void remove(long user, long id) {
        var slot = repository
            .lockSlot(id)
            .orElseThrow(() ->
                new ResponseStatusException(NOT_FOUND, "Slot not found.")
            );
        if (slot.providerUserId() != user) throw new ResponseStatusException(
            FORBIDDEN,
            "You can only remove your own availability."
        );
        if (repository.occupied(id)) throw new ResponseStatusException(
            CONFLICT,
            "A booked slot cannot be removed."
        );
        repository.remove(id);
    }

    public List<AppointmentDto> appointments(long user, boolean provider) {
        return repository.appointments(user, provider);
    }
}
