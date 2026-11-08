package com.smxplore.recommendations.infrastructure.adapters.in.web.security;

import com.smxplore.recommendations.domain.exception.InvalidJwtException;
import com.smxplore.recommendations.infrastructure.config.JwtProperties;
import com.smxplore.recommendations.infrastructure.security.JwtIdentityResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Traduce el JWT ya validado al usuario autenticado del servicio.
 *
 * <p>La identidad se resuelve con {@link JwtIdentityResolver} (MS05-14), la misma regla que usa
 * {@code AuthVersionValidator}, para que ambos acepten {@code sub} o el claim configurable.</p>
 */
@Component
@RequiredArgsConstructor
public class JwtUserResolver {

    private final JwtProperties properties;
    private final JwtIdentityResolver identityResolver;

    public AuthenticatedUser resolve(Jwt jwt) {
        UUID touristId = subjectId(jwt);
        String role = jwt.getClaimAsString(properties.getRoleClaim());
        if (role == null || role.isBlank()) {
            throw new InvalidJwtException("JWT must contain the role claim: " + properties.getRoleClaim());
        }

        String name = jwt.getClaimAsString(properties.getNameClaim());
        return new AuthenticatedUser(touristId, role, name);
    }

    public UUID subjectId(Jwt jwt) {
        return identityResolver.requireUserId(jwt);
    }
}
