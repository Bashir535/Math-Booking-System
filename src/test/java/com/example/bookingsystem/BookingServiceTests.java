package com.example.bookingsystem;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.bookingsystem.repository.BookingRepository;
import com.example.bookingsystem.service.BookingService;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class BookingServiceTests {

    BookingRepository repository;
    BookingService service;

    @BeforeEach
    void setup() {
        repository = mock(BookingRepository.class);
        service = new BookingService(repository);
    }

    void slot(boolean removed, int days) {
        when(repository.lockSlot(1)).thenReturn(
            Optional.of(
                new BookingRepository.LockedSlot(
                    1,
                    2,
                    3,
                    OffsetDateTime.now().plusDays(days),
                    removed
                )
            )
        );
    }

    @Test
    void availableSlotIsBooked() {
        slot(false, 1);
        when(repository.insert(4, 1, 2)).thenReturn(9L);
        assertThat(service.book(4, 1, 2)).isEqualTo(9);
    }

    @Test
    void rejectsOccupiedSlot() {
        slot(false, 1);
        when(repository.occupied(1)).thenReturn(true);
        assertThatThrownBy(() -> service.book(4, 1, 2))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("409");
        verify(repository, never()).insert(anyLong(), anyLong(), anyLong());
    }

    @Test
    void rejectsWrongService() {
        slot(false, 1);
        assertThatThrownBy(() -> service.book(4, 1, 8)).hasMessageContaining(
            "400"
        );
    }

    @Test
    void rejectsRemovedSlot() {
        slot(true, 1);
        assertThatThrownBy(() -> service.book(4, 1, 2)).hasMessageContaining(
            "409"
        );
    }

    @Test
    void rejectsPastSlot() {
        slot(false, -1);
        assertThatThrownBy(() -> service.book(4, 1, 2)).hasMessageContaining(
            "409"
        );
    }

    @Test
    void validatesIdentifiers() {
        assertThatThrownBy(() -> service.book(4, 0, 2)).hasMessageContaining(
            "400"
        );
        verifyNoInteractions(repository);
    }

    @Test
    void onlyOwnerCanCancel() {
        when(repository.find(9)).thenReturn(
            Optional.of(new BookingRepository.Appointment(9, 4, 1, "BOOKED"))
        );
        assertThatThrownBy(() -> service.cancel(5, 9)).hasMessageContaining(
            "403"
        );
        verify(repository, never()).cancel(anyLong());
    }

    @Test
    void ownerCanCancel() {
        slot(false, 1);
        when(repository.find(9)).thenReturn(
            Optional.of(new BookingRepository.Appointment(9, 4, 1, "BOOKED"))
        );
        service.cancel(4, 9);
        verify(repository).cancel(9);
    }

    @Test
    void cannotCancelPastAppointment() {
        slot(false, -1);
        when(repository.find(9)).thenReturn(
            Optional.of(new BookingRepository.Appointment(9, 4, 1, "BOOKED"))
        );
        assertThatThrownBy(() -> service.cancel(4, 9)).hasMessageContaining(
            "409"
        );
    }

    @Test
    void invalidAvailabilityRejected() {
        assertThatThrownBy(() ->
            service.create(3, 2, OffsetDateTime.now().minusDays(1))
        ).hasMessageContaining("400");
        verifyNoInteractions(repository);
    }

    @Test
    void cannotRemoveOtherTutorSlot() {
        slot(false, 1);
        assertThatThrownBy(() -> service.remove(8, 1)).hasMessageContaining(
            "403"
        );
    }

    @Test
    void cannotRemoveBookedSlot() {
        slot(false, 1);
        when(repository.occupied(1)).thenReturn(true);
        assertThatThrownBy(() -> service.remove(3, 1)).hasMessageContaining(
            "409"
        );
    }
}
