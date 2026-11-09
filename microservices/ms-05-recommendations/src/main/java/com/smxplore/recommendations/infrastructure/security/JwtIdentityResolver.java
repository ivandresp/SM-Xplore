package com.smxplore.recommendations.infrastructure.security;

import com.smxplore.recommendations.domain.exception.InvalidJwtException;
import com.smxplore.recommendations.infrastructure.config.JwtProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Resolución única del identificador de usuario de un JWT (MS05-14).
 *
 * <p>Antes de este componente cada consumidor tenía su propia regla: {@code JwtUserResolver}
 * aceptaba {@code sub} o el claim configurable {@code app.security.jwt.user-id-claim}, mientras
 * que {@code AuthVersionValidator} caía a un claim fijo {@code "id"}. Un token que solo traía el
 * claim configurado se resolvía para la identidad pero fallaba (401) en la comprobación de
 * versión. Aquí la regla es una sola y la usan ambos.</p>
 */
@Component
@RequiredArgsConstructor
public class JwtIdentityResolver {

    private final JwtProperties properties;

    /**
     * Identificador del usuario, o {@code null} si el token no lo trae (o no es un UUID).
     *
     * @param jwt token ya validado (firma, emisor y audiencia)
     */
    public UUID userIdOrNull(Jwt jwt) {
        String subject = jwt.getSubject();
        if (subject == null || subject.isBlank()) {
            subject = jwt.getClaimAsString(properties.getUserIdClaim());
        }
        if (subject == null || subject.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(subject);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    /**
     * Igual que {@link #userIdOrNull(Jwt)} pero falla con 401 cuando falta la identidad.
     *
     * @throws InvalidJwtException cuando el token no trae {@code sub} ni el claim configurado,
     *         o el valor no es un UUID
     */
    public UUID requireUserId(Jwt jwt) {
        String subject = jwt.getSubject();
        if (subject == null || subject.isBlank()) {
            subject = jwt.getClaimAsString(properties.getUserIdClaim());
        }
        if (subject == null || subject.isBlank()) {
            throw new InvalidJwtException(
                    "JWT must contain the sub claim or the configured userId claim: "
                            + properties.getUserIdClaim());
        }
        try {
            return UUID.fromString(subject);
        } catch (IllegalArgumentException exception) {
            throw new InvalidJwtException("JWT tourist id must be a UUID");
        }
    }
}
