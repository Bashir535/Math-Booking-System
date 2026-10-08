package com.example.bookingsystem.controller;

import com.example.bookingsystem.dto.SlotDto;
import com.example.bookingsystem.service.SlotService;
import java.time.LocalDate;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
public class SlotController {

    private final SlotService service;

    public SlotController(SlotService service) {
        this.service = service;
    }

    @GetMapping("/api/slots")
    public List<SlotDto> slots(
        @RequestParam(required = false) Long providerId,
        @RequestParam(required = false) Long serviceId,
        @RequestParam(required = false) LocalDate date,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "100") int size
    ) {
        return service.search(providerId, serviceId, date, page, size);
    }
}
