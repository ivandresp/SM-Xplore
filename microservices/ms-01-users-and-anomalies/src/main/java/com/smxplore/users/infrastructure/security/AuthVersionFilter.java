package com.smxplore.users.infrastructure.security;

import com.github.benmanes.caffeine.cache.AsyncCache;
import com.smxplore.users.application.port.out.UserRepository;
import com.smxplore.users.domain.model.Usuario;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

public class AuthVersionFilter implements WebFilter {

    private final UserRepository users;
    private final AsyncCache<UUID, Long> cache;

    public AuthVersionFilter(UserRepository users, AsyncCache<UUID, Long> cache) {
        this.users = users;
        this.cache = cache;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .filter(JwtAuthenticationToken.class::isInstance)
                .cast(JwtAuthenticationToken.class)
                .flatMap(authentication -> isCurrent(authentication.getToken()))
                .defaultIfEmpty(true)
                .flatMap(valid -> valid
                        ? chain.filter(exchange)
                        : SecurityProblemWriter.write(
                                exchange.getResponse(),
                                HttpStatus.UNAUTHORIZED,
                                "La sesión ya no es válida.",
                                "UNAUTHORIZED"));
    }

    private Mono<Boolean> isCurrent(Jwt jwt) {
        Object versionClaim = jwt.getClaim("ver");
        if (!(versionClaim instanceof Number version)) {
            return Mono.just(false);
        }

        UUID userId;
        try {
            userId = UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException | NullPointerException exception) {
            return Mono.just(false);
        }

        return currentVersion(userId).map(currentVersion -> currentVersion == version.longValue());
    }

    private Mono<Long> currentVersion(UUID userId) {
        return Mono.fromFuture(cache.get(
                userId,
                (id, executor) -> users.findById(id)
                        .map(Usuario::authVersion)
                        .defaultIfEmpty(-1L)
                        .toFuture()));
    }
}
