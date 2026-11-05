package com.smxplore.recommendations.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Valida que el token siga vigente en ms-01 ({@code ver}) y, cuando la política de producción lo
 * exige, que un administrador haya completado MFA ({@code mfa=true}).
 *
 * <p>La versión se cachea por usuario durante {@code cache-ttl}; una versión vieja se rechaza sin
 * esperar a que expire el JWT. Si ms-01 no responde, se falla cerrado.
 */
@Component
public class AuthVersionValidator {

    private static final String ADMIN = "ADMIN";
    private static final String ROLE_PREFIX = "ROLE_";

    private final AuthVersionProperties properties;
    private final JwtProperties jwtProperties;
    private final boolean requireAdminMfa;
    private final WebClient webClient;
    private final Map<UUID, CachedVersion> cache = new ConcurrentHashMap<>();

    public AuthVersionValidator(
            AuthVersionProperties properties,
            JwtProperties jwtProperties,
            @Value("${app.security.mfa.require-admin:false}") boolean requireAdminMfa
    ) {
        this.properties = properties;
        this.jwtProperties = jwtProperties;
        this.requireAdminMfa = requireAdminMfa;
        this.webClient = properties.isEnabled()
                ? WebClient.builder().baseUrl(properties.getBaseUrl()).build()
                : null;
    }

    public Mono<Jwt> validate(Jwt jwt) {
        return requireMfa(jwt)
                .then(validateVersion(jwt))
                .thenReturn(jwt);
    }

    private Mono<Void> requireMfa(Jwt jwt) {
        if (!requireAdminMfa) {
            return Mono.empty();
        }
        String role = jwt.getClaimAsString(jwtProperties.getRoleClaim());
        if (role == null) {
            return Mono.empty();
        }
        String normalized = role.startsWith(ROLE_PREFIX)
                ? role.substring(ROLE_PREFIX.length())
                : role;
        if (ADMIN.equals(normalized.toUpperCase(Locale.ROOT))
                && !Boolean.TRUE.equals(jwt.getClaim("mfa"))) {
            return Mono.error(new BadJwtException(
                    "Administrators must complete MFA before using this service"));
        }
        return Mono.empty();
    }

    private Mono<Void> validateVersion(Jwt jwt) {
        if (!properties.isEnabled()) {
            return Mono.empty();
        }
        Object versionClaim = jwt.getClaim("ver");
        if (!(versionClaim instanceof Number version)) {
            return Mono.error(new BadJwtException("Token must contain a numeric ver claim"));
        }
        UUID userId = userId(jwt);
        if (userId == null) {
            return Mono.error(new BadJwtException("Token must contain a user id"));
        }
        long tokenVersion = version.longValue();
        Instant now = Instant.now();
        CachedVersion cached = cache.get(userId);
        if (cached != null && cached.expiresAt().isAfter(now)) {
            if (cached.version() == tokenVersion) {
                return Mono.empty();
            }
            if (tokenVersion < cached.version()) {
                return invalidSession();
            }
            // Un token mas nuevo que la cache significa que la sesion se renovo
            // (logout/login, cambio de contrasena o de rol): hay que reconsultar
            // ms-01 antes de rechazarlo.
        }
        return currentVersion(jwt)
                .flatMap(current -> {
                    // La cache no retrocede: una respuesta lenta de ms-01 no puede
                    // reactivar un token ya revocado con una version menor.
                    CachedVersion updated = cache.compute(userId, (key, existing) ->
                            existing == null || current > existing.version()
                                    ? new CachedVersion(
                                            current,
                                            Instant.now().plus(properties.getCacheTtl()))
                                    : existing);
                    return updated.version() == tokenVersion ? Mono.empty() : invalidSession();
                })
                .onErrorMap(error -> error instanceof BadJwtException
                        ? error
                        : new BadJwtException("Could not validate the session version", error));
    }

    private Mono<Long> currentVersion(Jwt jwt) {
        return webClient.get()
                .uri("/api/v1/users/me/auth-version")
                .headers(headers -> headers.setBearerAuth(jwt.getTokenValue()))
                .retrieve()
                .bodyToMono(AuthVersionResponse.class)
                .timeout(properties.getTimeout())
                .map(AuthVersionResponse::authVersion);
    }

    private static Mono<Void> invalidSession() {
        return Mono.error(new BadJwtException("The session is no longer valid"));
    }

    private static UUID userId(Jwt jwt) {
        String subject = jwt.getSubject();
        if (subject == null || subject.isBlank()) {
            subject = jwt.getClaimAsString("id");
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

    public record AuthVersionResponse(long authVersion) {
    }

    private record CachedVersion(long version, Instant expiresAt) {
    }
}
