package com.example.bookingsystem.controller;

import com.example.bookingsystem.dto.SlotDto;
import com.example.bookingsystem.service.SlotService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
public class SlotController {
    private final SlotService service;

    public SlotController(SlotService service) {
        this.service = service;
    }

    @GetMapping("/api/slots")
    public List<SlotDto> slots() {
        return service.getAvailableSlots();
    }
}
