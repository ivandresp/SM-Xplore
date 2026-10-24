package com.smxplore.users.identity.domain.model;

import com.smxplore.users.identity.domain.exception.ValidationException;
import java.time.Instant;
import java.time.LocalDate;

public final class Turista extends Usuario {

    private final String dniType;
    private final String dniNumber;
    private String preferredLanguage;
    private LocalDate birthDate;
    private String nationality;

    private Turista(
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
            String preferredLanguage,
            LocalDate birthDate,
            String nationality) {
        super(id, fullName, email, phone, passwordHash, status, authVersion, signedUpAt, updatedAt, deletedAt);
        this.dniType = dniType;
        this.dniNumber = dniNumber;
        this.preferredLanguage = normalizeLanguage(preferredLanguage);
        this.birthDate = validateBirthDate(birthDate);
        this.nationality = normalize(nationality);
    }

    public static Turista register(
            UserId id,
            String fullName,
            Email email,
            String phone,
            PasswordHash passwordHash,
            String dniType,
            String dniNumber,
            String preferredLanguage,
            LocalDate birthDate,
            String nationality,
            Instant now) {
        return new Turista(
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
                preferredLanguage,
                birthDate,
                nationality);
    }

    public static Turista rehydrate(
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
            String preferredLanguage,
            LocalDate birthDate,
            String nationality) {
        return new Turista(
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
                preferredLanguage,
                birthDate,
                nationality);
    }

    public void updateDetails(String preferredLanguage, LocalDate birthDate, String nationality, Instant now) {
        if (preferredLanguage != null) {
            this.preferredLanguage = normalizeLanguage(preferredLanguage);
        }
        if (birthDate != null) {
            this.birthDate = validateBirthDate(birthDate);
        }
        if (nationality != null) {
            this.nationality = normalize(nationality);
        }
        markUpdated(now);
    }

    @Override
    public Role role() {
        return Role.TURISTA;
    }

    public String dniType() {
        return dniType;
    }

    public String dniNumber() {
        return dniNumber;
    }

    public String preferredLanguage() {
        return preferredLanguage;
    }

    public LocalDate birthDate() {
        return birthDate;
    }

    public String nationality() {
        return nationality;
    }

    private static String normalizeLanguage(String value) {
        return value == null || value.isBlank() ? "es" : value.trim();
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static LocalDate validateBirthDate(LocalDate birthDate) {
        if (birthDate != null && birthDate.isAfter(LocalDate.now())) {
            throw new ValidationException("La fecha de nacimiento no puede ser futura");
        }
        return birthDate;
    }
}
