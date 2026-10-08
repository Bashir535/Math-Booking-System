package com.example.bookingsystem.dto;

import java.time.OffsetDateTime;

public record AppointmentDto(
    long id,
    long slotId,
    String customerName,
    String providerName,
    String serviceName,
    OffsetDateTime startsAt,
    OffsetDateTime endsAt,
    String status
) {}
