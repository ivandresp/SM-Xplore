package com.smxplore.users.identity.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record PasswordResetToken(
        UUID id, UserId userId, String tokenHash, Instant createdAt, Instant expiresAt, Instant usedAt) {

    public PasswordResetToken {
        Objects.requireNonNull(id, "El identificador del token es obligatorio");
        Objects.requireNonNull(userId, "El usuario del token es obligatorio");
        Objects.requireNonNull(tokenHash, "El hash del token es obligatorio");
        Objects.requireNonNull(createdAt, "La fecha de creación del token es obligatoria");
        Objects.requireNonNull(expiresAt, "La fecha de expiración del token es obligatoria");
    }

    public static PasswordResetToken create(UserId userId, String tokenHash, Instant now, Duration ttl) {
        return new PasswordResetToken(UUID.randomUUID(), userId, tokenHash, now, now.plus(ttl), null);
    }

    public boolean isUsable(Instant now) {
        return usedAt == null && now.isBefore(expiresAt);
    }

    public PasswordResetToken markUsed(Instant now) {
        return new PasswordResetToken(id, userId, tokenHash, createdAt, expiresAt, now);
    }
}
