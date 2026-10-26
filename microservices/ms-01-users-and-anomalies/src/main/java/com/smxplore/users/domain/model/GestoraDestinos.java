package com.smxplore.users.domain.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class GestoraDestinos extends Usuario {

    private final String nit;
    private final LocalDate startOperationDate;

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
            LocalDate startOperationDate) {
        super(id, fullName, email, phone, passwordHash, status, authVersion, signedUpAt, updatedAt, deletedAt);
        this.nit = nit;
        this.startOperationDate = startOperationDate;
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
                startOperationDate);
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
            LocalDate startOperationDate) {
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
                startOperationDate);
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
}
