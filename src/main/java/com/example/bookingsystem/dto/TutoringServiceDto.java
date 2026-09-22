package com.example.bookingsystem.dto;

import java.math.BigDecimal;

public record TutoringServiceDto(long id, String name, String description,
                                int durationMinutes, BigDecimal price) {
}
