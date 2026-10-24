package com.smxplore.users.identity.application.model;

import java.time.Duration;
import java.util.Objects;

public record PasswordResetPolicy(Duration ttl) {

    public PasswordResetPolicy {
        Objects.requireNonNull(ttl, "La vigencia del token de recuperación es obligatoria");
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("La vigencia del token debe ser positiva");
        }
    }
}
