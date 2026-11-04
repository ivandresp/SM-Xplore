package com.smxplore.users.application.dto;

import java.time.LocalDate;

public record RegisterTouristCommand(
        String fullName,
        String email,
        String phone,
        String rawPassword,
        String dniType,
        String dniNumber,
        String preferredLanguage,
        LocalDate birthDate,
        String nationality) {}
