package com.smxplore.users.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record UpdateProfileCommand(
        String fullName,
        String phone,
        String preferredLanguage,
        LocalDate birthDate,
        String nationality,
        String businessType,
        BigDecimal latitude,
        BigDecimal longitude,
        String websiteUrl,
        String license) {}
