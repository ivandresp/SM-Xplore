package com.smxplore.securityreports.infrastructure.adapters.in.web.security;

import com.smxplore.securityreports.domain.exceptions.InvalidJwtException;
import com.smxplore.securityreports.domain.model.types.UserRef;
import com.smxplore.securityreports.infrastructure.config.JwtProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JwtUserResolver {

    private final JwtProperties jwtProperties;

    public UserRef resolve(Jwt jwt) {
        var subject = jwt.getSubject();
        if (subject == null || subject.isBlank()) {
            throw new InvalidJwtException("Token subject (sub) is missing.");
        }

        final UUID id;
        try {
            id = UUID.fromString(subject);
        } catch (IllegalArgumentException exception) {
            throw new InvalidJwtException("Token subject (sub) is not a valid UUID.", exception);
        }

        var name = jwt.getClaimAsString(jwtProperties.getNameClaim());
        if (name == null || name.isBlank()) {
            throw new InvalidJwtException("Token name claim is missing.");
        }

        return new UserRef(id, name);
    }

    public UUID subjectId(Jwt jwt) {
        return resolve(jwt).id();
    }
}
