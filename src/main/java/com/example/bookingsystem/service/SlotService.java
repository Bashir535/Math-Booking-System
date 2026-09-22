package com.example.bookingsystem.service;

import com.example.bookingsystem.dto.SlotDto;
import com.example.bookingsystem.repository.SlotRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

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
}
