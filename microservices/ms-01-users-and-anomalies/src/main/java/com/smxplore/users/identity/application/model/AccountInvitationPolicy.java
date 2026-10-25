package com.smxplore.users.identity.application.model;

import java.time.Duration;
import java.util.Objects;

public record AccountInvitationPolicy(Duration ttl) {

    public AccountInvitationPolicy {
        Objects.requireNonNull(ttl, "La vigencia de la invitación es obligatoria");
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("La vigencia de la invitación debe ser positiva");
        }
    }
}
