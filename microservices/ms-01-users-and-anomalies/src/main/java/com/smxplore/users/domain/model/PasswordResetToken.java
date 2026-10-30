package com.smxplore.users.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record PasswordResetToken(
        UUID id,
        UUID userId,
        String tokenHash,
        Instant createdAt,
        Instant expiresAt,
        Instant usedAt,
        TokenPurpose purpose) {

    public PasswordResetToken {
        Objects.requireNonNull(id, "El identificador del token es obligatorio");
        Objects.requireNonNull(userId, "El usuario del token es obligatorio");
        Objects.requireNonNull(tokenHash, "El hash del token es obligatorio");
        Objects.requireNonNull(createdAt, "La fecha de creación del token es obligatoria");
        Objects.requireNonNull(expiresAt, "La fecha de expiración del token es obligatoria");
        Objects.requireNonNull(purpose, "El propósito del token es obligatorio");
    }

    public static PasswordResetToken create(
            UUID userId, String tokenHash, Instant now, Duration ttl, TokenPurpose purpose) {
        return new PasswordResetToken(UUID.randomUUID(), userId, tokenHash, now, now.plus(ttl), null, purpose);
    }

    public boolean isUsable(Instant now) {
        return usedAt == null && now.isBefore(expiresAt);
    }

    public PasswordResetToken markUsed(Instant now) {
        return new PasswordResetToken(id, userId, tokenHash, createdAt, expiresAt, now, purpose);
    }
}
