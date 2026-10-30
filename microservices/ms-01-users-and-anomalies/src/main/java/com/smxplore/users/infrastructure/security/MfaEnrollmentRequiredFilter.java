package com.smxplore.users.infrastructure.security;

import com.smxplore.users.infrastructure.config.MfaProperties;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Obliga a los administradores a inscribir la autenticación multifactor cuando {@code required-for-admins} está
 * activo. Mientras no la activen sólo pueden consultar su perfil y gestionar la propia inscripción.
 */
public class MfaEnrollmentRequiredFilter implements WebFilter {

    private static final String REQUIRED_BODY = """
            {"type":"about:blank","title":"Forbidden","status":403,\
            "detail":"Tu cuenta debe activar la autenticación multifactor para continuar",\
            "code":"MFA_ENROLLMENT_REQUIRED"}\
            """;

    private final boolean requiredForAdmins;

    public MfaEnrollmentRequiredFilter(MfaProperties properties) {
        this.requiredForAdmins = properties.requiredForAdmins();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!requiredForAdmins) {
            return chain.filter(exchange);
        }
        return ReactiveSecurityContextHolder.getContext()
                .map(context -> context.getAuthentication())
                .filter(Authentication::isAuthenticated)
                .filter(authentication -> authentication.getPrincipal() instanceof Jwt)
                .map(authentication -> (Jwt) authentication.getPrincipal())
                .filter(this::isAdminWithoutMfa)
                .filter(jwt -> !isAllowed(exchange))
                .flatMap(jwt -> forbidden(exchange).thenReturn(Boolean.TRUE))
                .defaultIfEmpty(Boolean.FALSE)
                .flatMap(blocked -> Boolean.TRUE.equals(blocked) ? Mono.empty() : chain.filter(exchange));
    }

    private boolean isAdminWithoutMfa(Jwt jwt) {
        return "ADMIN".equals(jwt.getClaimAsString("role")) && !Boolean.TRUE.equals(jwt.getClaim("mfa"));
    }

    private static boolean isAllowed(ServerWebExchange exchange) {
        String path = exchange.getRequest().getPath().value();
        return path.startsWith("/api/v1/users/me/mfa")
                || path.equals("/api/v1/users/me")
                || path.equals("/api/v1/users/me/logout")
                || path.startsWith("/api/v1/auth")
                || path.startsWith("/api/v1/public")
                || path.startsWith("/actuator")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/.well-known");
    }

    private static Mono<Void> forbidden(ServerWebExchange exchange) {
        var response = exchange.getResponse();
        response.setStatusCode(HttpStatus.FORBIDDEN);
        response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        return response.writeWith(
                Mono.just(response.bufferFactory().wrap(REQUIRED_BODY.getBytes(StandardCharsets.UTF_8))));
    }
}
