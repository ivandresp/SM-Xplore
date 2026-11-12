package com.smxplore.users.application.dto;

import com.smxplore.users.domain.model.RegistrationStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Proyección plana de una guía para el listado de revisión que consume una gestora de destinos.
 */
public record GuideSummaryView(
        UUID id,
        String fullName,
        String email,
        String phone,
        String dniType,
        String dniNumber,
        String license,
        LocalDate startOperationDate,
        RegistrationStatus registrationStatus,
        Instant signedUpAt) {}
