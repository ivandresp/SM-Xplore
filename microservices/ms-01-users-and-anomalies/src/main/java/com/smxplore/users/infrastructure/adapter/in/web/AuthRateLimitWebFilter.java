package com.smxplore.users.infrastructure.adapter.in.web;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.smxplore.users.infrastructure.config.RateLimitProperties;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Limita la cantidad de solicitudes por dirección IP sobre los endpoints sensibles de autenticación. Es la primera
 * barrera contra fuerza bruta distribuida por cuenta; el bloqueo por cuenta lo aplica {@code LoginProtection}.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 15)
public class AuthRateLimitWebFilter implements WebFilter {

    private static final Duration WINDOW = Duration.ofMinutes(1);
    private static final String UNKNOWN_CLIENT = "desconocido";

    private final RateLimitProperties properties;
    private final Clock clock;
    private final Cache<String, Window> windows = Caffeine.newBuilder()
            .expireAfterAccess(WINDOW.multipliedBy(2))
            .maximumSize(10_000)
            .build();

    public AuthRateLimitWebFilter(RateLimitProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!properties.enabled() || exchange.getRequest().getMethod() != HttpMethod.POST) {
            return chain.filter(exchange);
        }
        String bucket = bucketFor(exchange.getRequest().getPath().value());
        if (bucket == null) {
            return chain.filter(exchange);
        }

        Instant now = clock.instant();
        String key = bucket + "|" + clientAddress(exchange);
        Window window = windows.get(key, ignored -> new Window(now));
        if (!window.tryAcquire(now, properties.requestsPerMinute())) {
            return reject(exchange, window.retryAfterSeconds(now));
        }
        return chain.filter(exchange);
    }

    private Mono<Void> reject(ServerWebExchange exchange, long retryAfterSeconds) {
        var response = exchange.getResponse();
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        response.getHeaders().set(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds));

        String body = """
                {"type":"about:blank","title":"Too Many Requests","status":429,\
                "detail":"Demasiadas solicitudes; espera antes de volver a intentarlo",\
                "code":"RATE_LIMIT_EXCEEDED","retryAfterSeconds":%d}\
                """.formatted(retryAfterSeconds);
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    private static String bucketFor(String path) {
        return switch (path) {
            case "/api/v1/auth/login" -> "login";
            case "/api/v1/auth/forgot-password" -> "forgot-password";
            case "/api/v1/auth/reset-password" -> "reset-password";
            case "/api/v1/auth/mfa/verify" -> "mfa-verify";
            default -> null;
        };
    }

    private static String clientAddress(ServerWebExchange exchange) {
        InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
        return remoteAddress == null || remoteAddress.getAddress() == null
                ? UNKNOWN_CLIENT
                : remoteAddress.getAddress().getHostAddress();
    }

    private static final class Window {

        private Instant start;
        private final AtomicInteger count = new AtomicInteger();

        private Window(Instant start) {
            this.start = start;
        }

        private synchronized boolean tryAcquire(Instant now, int limit) {
            if (!now.isBefore(start.plus(WINDOW))) {
                start = now;
                count.set(0);
            }
            return count.incrementAndGet() <= limit;
        }

        private synchronized long retryAfterSeconds(Instant now) {
            long elapsed = Math.max(0, Duration.between(start, now).toSeconds());
            return Math.max(1, WINDOW.toSeconds() - elapsed);
        }
    }
}
