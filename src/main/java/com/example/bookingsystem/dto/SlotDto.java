package com.example.bookingsystem.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record SlotDto(long id, long providerId, String providerName,
                      long serviceId, String serviceName, OffsetDateTime startsAt,
                      OffsetDateTime endsAt, BigDecimal price) {
}
