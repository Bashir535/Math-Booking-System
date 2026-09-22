package com.example.bookingsystem.controller;

import com.example.bookingsystem.dto.HomeDto;
import com.example.bookingsystem.service.CatalogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
public class CatalogController {
    private final CatalogService service;

    public CatalogController(CatalogService service) {
        this.service = service;
    }

    @GetMapping("/api/home")
    public HomeDto home() {
        return service.getHome();
    }

}
