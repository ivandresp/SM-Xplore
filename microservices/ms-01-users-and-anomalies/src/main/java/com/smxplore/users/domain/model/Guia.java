package com.smxplore.users.domain.model;

import com.smxplore.users.domain.exception.ValidationException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Guía turístico.
 *
 * <p>Desde el registro público el alta nace en {@link RegistrationStatus#PENDIENTE} y solo una gestora puede
 * aprobarla; mientras tanto la cuenta no puede iniciar sesión. Un alta creada internamente por un administrador nace
 * aprobada, porque quien la crea es la misma autoridad que aprobaría la solicitud.
 */
public final class Guia extends Usuario implements RegistrationReviewable {

    private final String dniType;
    private final String dniNumber;
    private String license;
    private RegistrationStatus registrationStatus;
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
            RegistrationStatus registrationStatus,
            LocalDate startOperationDate) {
        super(id, fullName, email, phone, passwordHash, status, authVersion, signedUpAt, updatedAt, deletedAt);
        this.dniType = dniType;
        this.dniNumber = dniNumber;
        this.license = validateLicense(license);
        this.registrationStatus = registrationStatus == null ? RegistrationStatus.PENDIENTE : registrationStatus;
        this.startOperationDate = startOperationDate;
    }

    /**
     * Alta creada por un administrador: la cuenta queda aprobada porque el actor ya es la autoridad que revisa.
     */
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
                RegistrationStatus.APROBADO,
                startOperationDate);
    }

    /**
     * Solicitud pública de registro: la cuenta nace pendiente de revisión y no puede iniciar sesión todavía.
     */
    public static Guia requestRegistration(
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
                RegistrationStatus.PENDIENTE,
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
            RegistrationStatus registrationStatus,
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
                registrationStatus,
                startOperationDate);
    }

    public void updateLicense(String license, Instant now) {
        if (license != null) {
            this.license = validateLicense(license);
            markUpdated(now);
        }
    }

    /**
     * Cambia el estado del alta e incrementa {@code authVersion}: aprobar o rechazar altera el permiso de acceso, así
     * que cualquier token emitido antes de la decisión deja de servir.
     */
    @Override
    public void changeRegistrationStatus(RegistrationStatus newStatus, Instant now) {
        this.registrationStatus = newStatus;
        incrementAuthVersion();
        markUpdated(now);
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

    @Override
    public RegistrationStatus registrationStatus() {
        return registrationStatus;
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
