package com.smxplore.users.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RegisterProviderCommand(
        String fullName,
        String email,
        String phone,
        String rawPassword,
        String nit,
        String businessType,
        BigDecimal latitude,
        BigDecimal longitude,
        LocalDate startOperationDate) {}
