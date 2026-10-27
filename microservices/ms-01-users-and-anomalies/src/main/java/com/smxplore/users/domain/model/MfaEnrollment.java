package com.smxplore.users.domain.model;

import com.smxplore.users.domain.exception.ValidationException;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Inscripción TOTP de un usuario. El secreto se guarda en base32 y sólo se muestra al usuario durante la inscripción.
 */
public record MfaEnrollment(
        UUID userId, String secret, boolean enabled, Long lastUsedStep, Instant createdAt, Instant confirmedAt) {

    public MfaEnrollment {
        Objects.requireNonNull(userId, "El usuario es obligatorio");
        if (secret == null || secret.isBlank()) {
            throw new ValidationException("El secreto de autenticación multifactor es obligatorio");
        }
        Objects.requireNonNull(createdAt, "La fecha de creación es obligatoria");
    }

    public static MfaEnrollment pending(UUID userId, String secret, Instant now) {
        return new MfaEnrollment(userId, secret, false, null, now, null);
    }

    public MfaEnrollment confirm(Instant now) {
        return new MfaEnrollment(userId, secret, true, lastUsedStep, createdAt, now);
    }

    public MfaEnrollment withLastUsedStep(long step) {
        return new MfaEnrollment(userId, secret, enabled, step, createdAt, confirmedAt);
    }

    public boolean isPending() {
        return !enabled;
    }
}
