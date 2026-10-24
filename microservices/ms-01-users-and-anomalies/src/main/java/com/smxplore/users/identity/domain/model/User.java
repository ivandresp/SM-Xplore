package com.smxplore.users.identity.domain.model;

import com.smxplore.users.identity.domain.exception.ProfileMismatchException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

public final class User {

    private final UserId id;
    private final FullName fullName;
    private final Email email;
    private final Phone phone;
    private final PasswordHash passwordHash;
    private final Profile profile;
    private final UserStatus status;
    private final long authVersion;
    private final Instant signedUpAt;
    private final Instant updatedAt;
    private final Instant deletedAt;

    private User(
            UserId id,
            FullName fullName,
            Email email,
            Phone phone,
            PasswordHash passwordHash,
            Profile profile,
            UserStatus status,
            long authVersion,
            Instant signedUpAt,
            Instant updatedAt,
            Instant deletedAt) {
        this.id = Objects.requireNonNull(id, "El identificador de usuario es obligatorio");
        this.fullName = Objects.requireNonNull(fullName, "El nombre completo es obligatorio");
        this.email = Objects.requireNonNull(email, "El correo electrónico es obligatorio");
        this.phone = phone;
        this.passwordHash = Objects.requireNonNull(passwordHash, "La contraseña es obligatoria");
        this.profile = Objects.requireNonNull(profile, "El perfil es obligatorio");
        this.status = Objects.requireNonNull(status, "El estado es obligatorio");
        this.authVersion = authVersion;
        this.signedUpAt = Objects.requireNonNull(signedUpAt, "La fecha de registro es obligatoria");
        this.updatedAt = Objects.requireNonNull(updatedAt, "La fecha de actualización es obligatoria");
        this.deletedAt = deletedAt;
    }

    public static User registerTourist(
            UserId id,
            FullName fullName,
            Email email,
            Phone phone,
            PasswordHash passwordHash,
            Dni dni,
            String preferredLanguage,
            LocalDate birthDate,
            String nationality,
            Instant now) {
        return new User(
                id,
                fullName,
                email,
                phone,
                passwordHash,
                new TouristProfile(dni, preferredLanguage, birthDate, nationality),
                UserStatus.ACTIVO,
                0L,
                now,
                now,
                null);
    }

    public static User registerProvider(
            UserId id,
            FullName fullName,
            Email email,
            Phone phone,
            PasswordHash passwordHash,
            Nit nit,
            String businessType,
            BigDecimal latitude,
            BigDecimal longitude,
            LocalDate startOperationDate,
            Instant now) {
        return new User(
                id,
                fullName,
                email,
                phone,
                passwordHash,
                new ProviderProfile(
                        nit,
                        businessType,
                        ProviderRegistrationStatus.PENDIENTE,
                        latitude,
                        longitude,
                        startOperationDate),
                UserStatus.ACTIVO,
                0L,
                now,
                now,
                null);
    }

    public static User createAdmin(
            UserId id, FullName fullName, Email email, Phone phone, PasswordHash passwordHash, Dni dni, Instant now) {
        return new User(
                id,
                fullName,
                email,
                phone,
                passwordHash,
                new AdminProfile(dni, null),
                UserStatus.ACTIVO,
                0L,
                now,
                now,
                null);
    }

    public static User rehydrate(
            UserId id,
            FullName fullName,
            Email email,
            Phone phone,
            PasswordHash passwordHash,
            Profile profile,
            UserStatus status,
            long authVersion,
            Instant signedUpAt,
            Instant updatedAt,
            Instant deletedAt) {
        return new User(
                id,
                fullName,
                email,
                phone,
                passwordHash,
                profile,
                status,
                authVersion,
                signedUpAt,
                updatedAt,
                deletedAt);
    }

    public User updateProfile(FullName newFullName, Phone newPhone, Profile newProfile, Instant now) {
        if (newProfile.role() != role()) {
            throw new ProfileMismatchException("El perfil no corresponde al rol del usuario");
        }
        return new User(
                id,
                newFullName,
                email,
                newPhone,
                passwordHash,
                newProfile,
                status,
                authVersion,
                signedUpAt,
                now,
                deletedAt);
    }

    public User changeRole(FullName newFullName, Phone newPhone, Profile newProfile, Instant now) {
        return new User(
                id,
                newFullName,
                email,
                newPhone,
                passwordHash,
                newProfile,
                status,
                authVersion + 1,
                signedUpAt,
                now,
                deletedAt);
    }

    public User changePassword(PasswordHash newPasswordHash, Instant now) {
        return new User(
                id,
                fullName,
                email,
                phone,
                newPasswordHash,
                profile,
                status,
                authVersion + 1,
                signedUpAt,
                now,
                deletedAt);
    }

    public User changeStatus(UserStatus newStatus, Instant now) {
        if (newStatus == status) {
            return this;
        }
        return new User(
                id,
                fullName,
                email,
                phone,
                passwordHash,
                profile,
                newStatus,
                authVersion + 1,
                signedUpAt,
                now,
                newStatus == UserStatus.ELIMINADO ? now : deletedAt);
    }

    public User changeProviderRegistrationStatus(ProviderRegistrationStatus newStatus, Instant now) {
        if (!(profile instanceof ProviderProfile providerProfile)) {
            throw new ProfileMismatchException("El usuario no es una prestadora de servicios");
        }
        return new User(
                id,
                fullName,
                email,
                phone,
                passwordHash,
                providerProfile.withRegistrationStatus(newStatus),
                status,
                authVersion + 1,
                signedUpAt,
                now,
                deletedAt);
    }

    public boolean isActive() {
        return status == UserStatus.ACTIVO;
    }

    public Role role() {
        return profile.role();
    }

    public UserId id() {
        return id;
    }

    public FullName fullName() {
        return fullName;
    }

    public Email email() {
        return email;
    }

    public Phone phone() {
        return phone;
    }

    public PasswordHash passwordHash() {
        return passwordHash;
    }

    public Profile profile() {
        return profile;
    }

    public UserStatus status() {
        return status;
    }

    public long authVersion() {
        return authVersion;
    }

    public Instant signedUpAt() {
        return signedUpAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public Instant deletedAt() {
        return deletedAt;
    }
}
