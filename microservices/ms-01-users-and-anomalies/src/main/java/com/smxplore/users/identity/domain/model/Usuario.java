package com.smxplore.users.identity.domain.model;

import com.smxplore.users.identity.domain.exception.ValidationException;
import java.time.Instant;
import java.util.Objects;

public abstract sealed class Usuario permits Turista, Guia, PrestadoraServicios, GestoraDestinos, Admin {

    private final UserId id;
    private final Email email;
    private String fullName;
    private String phone;
    private PasswordHash passwordHash;
    private UserStatus status;
    private long authVersion;
    private final Instant signedUpAt;
    private Instant updatedAt;
    private Instant deletedAt;

    protected Usuario(
            UserId id,
            String fullName,
            Email email,
            String phone,
            PasswordHash passwordHash,
            UserStatus status,
            long authVersion,
            Instant signedUpAt,
            Instant updatedAt,
            Instant deletedAt) {
        if (fullName == null || fullName.isBlank()) {
            throw new ValidationException("El nombre completo es obligatorio");
        }
        this.id = Objects.requireNonNull(id, "El identificador de usuario es obligatorio");
        this.fullName = fullName;
        this.email = Objects.requireNonNull(email, "El correo electrónico es obligatorio");
        this.phone = phone;
        this.passwordHash = Objects.requireNonNull(passwordHash, "La contraseña es obligatoria");
        this.status = Objects.requireNonNull(status, "El estado es obligatorio");
        this.authVersion = authVersion;
        this.signedUpAt = Objects.requireNonNull(signedUpAt, "La fecha de registro es obligatoria");
        this.updatedAt = Objects.requireNonNull(updatedAt, "La fecha de actualización es obligatoria");
        this.deletedAt = deletedAt;
    }

    public final void updateContact(String newFullName, String newPhone, Instant now) {
        if (newFullName == null || newFullName.isBlank()) {
            throw new ValidationException("El nombre completo es obligatorio");
        }
        this.fullName = newFullName;
        this.phone = newPhone;
        this.updatedAt = now;
    }

    public final void changePassword(PasswordHash newPasswordHash, Instant now) {
        this.passwordHash = Objects.requireNonNull(newPasswordHash);
        this.authVersion++;
        this.updatedAt = now;
    }

    public final void changeStatus(UserStatus newStatus, Instant now) {
        if (newStatus == status) {
            return;
        }
        this.status = Objects.requireNonNull(newStatus);
        this.authVersion++;
        this.updatedAt = now;
        if (newStatus == UserStatus.ELIMINADO) {
            this.deletedAt = now;
        }
    }

    protected final void markUpdated(Instant now) {
        this.updatedAt = now;
    }

    protected final void incrementAuthVersion() {
        this.authVersion++;
    }

    public abstract Role role();

    public final boolean isActive() {
        return status == UserStatus.ACTIVO;
    }

    public final UserId id() {
        return id;
    }

    public final String fullName() {
        return fullName;
    }

    public final Email email() {
        return email;
    }

    public final String phone() {
        return phone;
    }

    public final PasswordHash passwordHash() {
        return passwordHash;
    }

    public final UserStatus status() {
        return status;
    }

    public final long authVersion() {
        return authVersion;
    }

    public final Instant signedUpAt() {
        return signedUpAt;
    }

    public final Instant updatedAt() {
        return updatedAt;
    }

    public final Instant deletedAt() {
        return deletedAt;
    }
}
