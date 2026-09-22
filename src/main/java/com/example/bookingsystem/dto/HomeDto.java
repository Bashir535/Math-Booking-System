package com.example.bookingsystem.dto;

import java.util.List;

public record HomeDto(String title, String description, String timeZone,
                      List<ProviderDto> providers, List<TutoringServiceDto> services) {
}
