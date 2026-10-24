package com.smxplore.users.identity.domain.model;

import java.time.Instant;

public final class Admin extends Usuario {

    private final String dniType;
    private final String dniNumber;
    private Instant lastAccessDate;

    private Admin(
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
            String dniType,
            String dniNumber,
            Instant lastAccessDate) {
        super(id, fullName, email, phone, passwordHash, status, authVersion, signedUpAt, updatedAt, deletedAt);
        this.dniType = dniType;
        this.dniNumber = dniNumber;
        this.lastAccessDate = lastAccessDate;
    }

    public static Admin create(
            UserId id,
            String fullName,
            Email email,
            String phone,
            PasswordHash passwordHash,
            String dniType,
            String dniNumber,
            Instant now) {
        return new Admin(
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
                null);
    }

    public static Admin rehydrate(
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
            String dniType,
            String dniNumber,
            Instant lastAccessDate) {
        return new Admin(
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
                lastAccessDate);
    }

    public void recordAccess(Instant now) {
        this.lastAccessDate = now;
        markUpdated(now);
    }

    @Override
    public Role role() {
        return Role.ADMIN;
    }

    public String dniType() {
        return dniType;
    }

    public String dniNumber() {
        return dniNumber;
    }

    public Instant lastAccessDate() {
        return lastAccessDate;
    }
}
