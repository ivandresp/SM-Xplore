package com.smxplore.users.shared.infrastructure.config;

import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.security.account-invitation")
public record AccountInvitationProperties(Duration ttl) {

    public AccountInvitationProperties {
        Objects.requireNonNull(ttl, "La vigencia de la invitación es obligatoria");
    }
}
