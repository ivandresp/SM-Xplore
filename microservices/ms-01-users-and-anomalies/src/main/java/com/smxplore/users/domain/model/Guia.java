package com.smxplore.users.domain.model;

import com.smxplore.users.domain.exception.ValidationException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class Guia extends Usuario {

    private final String dniType;
    private final String dniNumber;
    private String license;
    private final LocalDate startOperationDate;

    private Guia(
            UUID id,
            String fullName,
            Email email,
            String phone,
            PasswordHash passwordHash,
            UserStatus status,
            long authVersion,
            Instant signedUpAt,
            Instant updatedAt,
            Instant deletedAt,
            String dniType,
            String dniNumber,
            String license,
            LocalDate startOperationDate) {
        super(id, fullName, email, phone, passwordHash, status, authVersion, signedUpAt, updatedAt, deletedAt);
        this.dniType = dniType;
        this.dniNumber = dniNumber;
        this.license = validateLicense(license);
        this.startOperationDate = startOperationDate;
    }

    public static Guia register(
            UUID id,
            String fullName,
            Email email,
            String phone,
            PasswordHash passwordHash,
            String dniType,
            String dniNumber,
            String license,
            LocalDate startOperationDate,
            Instant now) {
        return new Guia(
                id,
                fullName,
                email,
                phone,
                passwordHash,
                UserStatus.ACTIVO,
                0L,
                now,
                now,
                null,
                dniType,
                dniNumber,
                license,
                startOperationDate);
    }

    public static Guia rehydrate(
            UUID id,
            String fullName,
            Email email,
            String phone,
            PasswordHash passwordHash,
            UserStatus status,
            long authVersion,
            Instant signedUpAt,
            Instant updatedAt,
            Instant deletedAt,
            String dniType,
            String dniNumber,
            String license,
            LocalDate startOperationDate) {
        return new Guia(
                id,
                fullName,
                email,
                phone,
                passwordHash,
                status,
                authVersion,
                signedUpAt,
                updatedAt,
                deletedAt,
                dniType,
                dniNumber,
                license,
                startOperationDate);
    }

    public void updateLicense(String license, Instant now) {
        if (license != null) {
            this.license = validateLicense(license);
            markUpdated(now);
        }
    }

    @Override
    public Role role() {
        return Role.GUIDE;
    }

    public String dniType() {
        return dniType;
    }

    public String dniNumber() {
        return dniNumber;
    }

    public String license() {
        return license;
    }

    public LocalDate startOperationDate() {
        return startOperationDate;
    }

    private static String validateLicense(String license) {
        if (license == null || license.isBlank()) {
            throw new ValidationException("La licencia del guía es obligatoria");
        }
        String normalized = license.trim();
        if (normalized.length() > 50) {
            throw new ValidationException("La licencia del guía no puede superar 50 caracteres");
        }
        return normalized;
    }
}
