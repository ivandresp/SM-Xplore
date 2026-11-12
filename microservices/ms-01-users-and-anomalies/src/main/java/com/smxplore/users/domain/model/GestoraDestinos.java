package com.smxplore.users.domain.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Gestora de destinos.
 *
 * <p>Desde el registro público el alta nace en {@link RegistrationStatus#PENDIENTE} y solo un administrador puede
 * aprobarla; mientras tanto la cuenta no puede iniciar sesión. Un alta creada internamente por un administrador nace
 * aprobada, porque quien la crea es la misma autoridad que aprobaría la solicitud.
 */
public final class GestoraDestinos extends Usuario implements RegistrationReviewable {

    private final String nit;
    private final LocalDate startOperationDate;
    private RegistrationStatus registrationStatus;

    private GestoraDestinos(
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
            String nit,
            LocalDate startOperationDate,
            RegistrationStatus registrationStatus) {
        super(id, fullName, email, phone, passwordHash, status, authVersion, signedUpAt, updatedAt, deletedAt);
        this.nit = nit;
        this.startOperationDate = startOperationDate;
        this.registrationStatus = registrationStatus == null ? RegistrationStatus.PENDIENTE : registrationStatus;
    }

    /**
     * Alta creada por un administrador: la cuenta queda aprobada porque el actor ya es la autoridad que revisa.
     */
    public static GestoraDestinos register(
            UUID id,
            String fullName,
            Email email,
            String phone,
            PasswordHash passwordHash,
            String nit,
            LocalDate startOperationDate,
            Instant now) {
        return new GestoraDestinos(
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
                nit,
                startOperationDate,
                RegistrationStatus.APROBADO);
    }

    /**
     * Solicitud pública de registro: la cuenta nace pendiente de revisión y no puede iniciar sesión todavía.
     */
    public static GestoraDestinos requestRegistration(
            UUID id,
            String fullName,
            Email email,
            String phone,
            PasswordHash passwordHash,
            String nit,
            LocalDate startOperationDate,
            Instant now) {
        return new GestoraDestinos(
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
                nit,
                startOperationDate,
                RegistrationStatus.PENDIENTE);
    }

    public static GestoraDestinos rehydrate(
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
            String nit,
            LocalDate startOperationDate,
            RegistrationStatus registrationStatus) {
        return new GestoraDestinos(
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
                nit,
                startOperationDate,
                registrationStatus);
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
        return Role.DESTINATION_MANAGER;
    }

    public String nit() {
        return nit;
    }

    public LocalDate startOperationDate() {
        return startOperationDate;
    }

    @Override
    public RegistrationStatus registrationStatus() {
        return registrationStatus;
    }
}
