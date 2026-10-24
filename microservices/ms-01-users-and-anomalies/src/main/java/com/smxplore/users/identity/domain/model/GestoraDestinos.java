package com.smxplore.users.identity.domain.model;

import java.time.Instant;
import java.time.LocalDate;

public final class GestoraDestinos extends Usuario {

    private final String nit;
    private final LocalDate startOperationDate;

    private GestoraDestinos(
            UserId id,
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
            UserId id,
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
            UserId id,
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
        return Role.GESTORA_DESTINOS;
    }

    public String nit() {
        return nit;
    }

    public LocalDate startOperationDate() {
        return startOperationDate;
    }
}
