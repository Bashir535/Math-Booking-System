package com.example.bookingsystem.dto;

public record AccountDto(
    long id,
    String username,
    String fullName,
    String role,
    Long providerId
) {}
