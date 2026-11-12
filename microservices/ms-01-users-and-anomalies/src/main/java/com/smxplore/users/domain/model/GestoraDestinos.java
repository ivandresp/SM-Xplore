package com.smxplore.users.domain.model;

import com.smxplore.users.domain.exception.InvalidStatusTransitionException;
import com.smxplore.users.domain.exception.ValidationException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class GestoraDestinos extends Usuario {

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

    public static GestoraDestinos register(
            UUID id,
            String fullName,
            Email email,
            String phone,
            PasswordHash passwordHash,
            String nit,
            LocalDate startOperationDate,
            Instant now) {
        return register(
                id, fullName, email, phone, passwordHash, nit, startOperationDate, RegistrationStatus.PENDIENTE, now);
    }

    /**
     * Alta controlada por un administrador: puede nacer ya aprobada. El auto-registro público siempre usa
     * {@link #register(UUID, String, Email, String, PasswordHash, String, LocalDate, Instant)}.
     */
    public static GestoraDestinos register(
            UUID id,
            String fullName,
            Email email,
            String phone,
            PasswordHash passwordHash,
            String nit,
            LocalDate startOperationDate,
            RegistrationStatus registrationStatus,
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
                registrationStatus);
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
     * Cambia el estado de aprobación del registro. Repetir el mismo estado es una transición inválida; una decisión
     * previa (aprobado o rechazado) sí puede corregirse porque la revisión es administrativa.
     */
    public void changeRegistrationStatus(RegistrationStatus newStatus, Instant now) {
        if (newStatus == null) {
            throw new ValidationException("El estado de registro es obligatorio");
        }
        if (newStatus == this.registrationStatus) {
            throw new InvalidStatusTransitionException(
                    "La gestora de destinos ya se encuentra en estado " + newStatus.name());
        }
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

    public RegistrationStatus registrationStatus() {
        return registrationStatus;
    }
}
