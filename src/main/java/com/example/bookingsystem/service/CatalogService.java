package com.example.bookingsystem.service;

import com.example.bookingsystem.dto.HomeDto;
import com.example.bookingsystem.dto.ProviderDto;
import com.example.bookingsystem.dto.TutoringServiceDto;
import com.example.bookingsystem.repository.CatalogRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class CatalogService {
    private final CatalogRepository repository;
    private final String timeZone;

    public CatalogService(CatalogRepository repository,
                          @Value("${booking.time-zone}") String timeZone) {
        this.repository = repository;
        this.timeZone = timeZone;
    }

    public HomeDto getHome() {
        return new HomeDto("Booking System", "Math tutoring appointments", timeZone,
                getProviders(), getServices());
    }

    public List<ProviderDto> getProviders() {
        return repository.findProviders();
    }

    public List<TutoringServiceDto> getServices() {
        return repository.findServices();
    }
}
