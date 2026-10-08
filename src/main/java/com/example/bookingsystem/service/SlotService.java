package com.example.bookingsystem.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

import com.example.bookingsystem.dto.SlotDto;
import com.example.bookingsystem.repository.SlotRepository;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class SlotService {

    private final SlotRepository repository;

    public SlotService(SlotRepository repository) {
        this.repository = repository;
    }

    public List<SlotDto> getAvailableSlots() {
        return repository.findAvailable();
    }

    public List<SlotDto> search(
        Long provider,
        Long service,
        LocalDate date,
        int page,
        int size
    ) {
        if (
            page < 0 ||
            page > 100000 ||
            size < 1 ||
            size > 100 ||
            (provider != null && provider <= 0) ||
            (service != null && service <= 0)
        ) throw new ResponseStatusException(
            BAD_REQUEST,
            "Invalid filter or page size."
        );
        return repository.search(provider, service, date, page, size);
    }
}
