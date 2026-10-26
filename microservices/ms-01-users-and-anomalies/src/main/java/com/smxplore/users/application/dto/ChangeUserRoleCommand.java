package com.smxplore.users.application.dto;

import com.smxplore.users.domain.model.Role;
import java.math.BigDecimal;
import java.time.LocalDate;

public record ChangeUserRoleCommand(
        Role role,
        String dniType,
        String dniNumber,
        String preferredLanguage,
        LocalDate birthDate,
        String nationality,
        String license,
        String nit,
        String businessType,
        BigDecimal latitude,
        BigDecimal longitude,
        String websiteUrl,
        LocalDate startOperationDate) {}
