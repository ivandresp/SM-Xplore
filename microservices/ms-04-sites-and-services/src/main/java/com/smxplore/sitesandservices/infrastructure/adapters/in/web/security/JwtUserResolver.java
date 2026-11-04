package com.smxplore.sitesandservices.infrastructure.adapters.in.web.security;

import com.smxplore.sitesandservices.domain.exception.InvalidJwtException;
import com.smxplore.sitesandservices.domain.model.common.Actor;
import com.smxplore.sitesandservices.domain.model.common.UserRole;
import com.smxplore.sitesandservices.infrastructure.config.JwtProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JwtUserResolver {

    private final JwtProperties properties;

    public Actor resolve(Jwt jwt) {
        UUID userId = userId(jwt);
        String role = jwt.getClaimAsString(properties.getRoleClaim());
        UserRole userRole = UserRole.fromClaim(role);
        String name = jwt.getClaimAsString(properties.getNameClaim());
        return new Actor(userId, userRole, name);
    }

    public UUID userId(Jwt jwt) {
        return parseUuid(identity(jwt));
    }

    private String identity(Jwt jwt) {
        String subject = jwt.getSubject();
        if (subject == null || subject.isBlank()) {
            subject = jwt.getClaimAsString(properties.getUserIdClaim());
        }
        if (subject == null || subject.isBlank()) {
            throw new InvalidJwtException(
                    "JWT must contain the sub claim or the configured userId claim: "
                            + properties.getUserIdClaim());
        }
        return subject;
    }

    private UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw new InvalidJwtException("JWT user id must be a UUID");
        }
    }
}
