package com.smxplore.recommendations.infrastructure.adapters.in.web.security;

import com.smxplore.recommendations.domain.exception.InvalidJwtException;
import com.smxplore.recommendations.infrastructure.config.JwtProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JwtUserResolver {

    private final JwtProperties properties;

    public AuthenticatedUser resolve(Jwt jwt) {
        UUID touristId = subjectId(jwt);
        String role = jwt.getClaimAsString(properties.getRoleClaim());
        if (role == null || role.isBlank()) {
            throw new InvalidJwtException("JWT must contain the role claim: " + properties.getRoleClaim());
        }

        String name = jwt.getClaimAsString(properties.getNameClaim());
        String language = jwt.getClaimAsString(properties.getLanguageClaim());
        return new AuthenticatedUser(
                touristId,
                role,
                name,
                language == null || language.isBlank() ? "es" : language
        );
    }

    public UUID subjectId(Jwt jwt) {
        String subject = jwt.getSubject();
        if (subject == null || subject.isBlank()) {
            throw new InvalidJwtException("JWT subject (sub) is required");
        }
        try {
            return UUID.fromString(subject);
        } catch (IllegalArgumentException exception) {
            throw new InvalidJwtException("JWT subject (sub) must be a UUID");
        }
    }
}
