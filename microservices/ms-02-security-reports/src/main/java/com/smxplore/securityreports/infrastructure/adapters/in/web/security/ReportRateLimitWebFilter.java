package com.smxplore.securityreports.infrastructure.adapters.in.web.security;

import com.smxplore.securityreports.infrastructure.adapters.in.web.error.ProblemDetails;
import com.smxplore.securityreports.infrastructure.config.RateLimitProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

import java.security.Principal;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Limite de escrituras por cuenta (MS02-03).
 *
 * <p>La version anterior contaba solo la creacion de reportes y usaba {@code remoteAddress} como clave: detras del
 * gateway todas las peticiones llegan con la IP del proxy, asi que el cupo era global. Ahora la clave es el
 * {@code sub} del JWT y, solo cuando no hay principal, la IP del cliente resuelta por {@link ClientAddressResolver}
 * (que ignora encabezados de reenvio de clientes no confiables). Cubre tambien comentarios, fotos, cambio de
 * contenido, cierre y resolucion, cada uno con su propio cupo.
 */
@Component
@RequiredArgsConstructor
public class ReportRateLimitWebFilter implements WebFilter {

    private static final String REPORTS_PATH = "/api/v1/security-reports";
    private static final String COMMENTS_SUFFIX = "/comments";
    private static final int WINDOW_SECONDS = 60;
    private static final String RETRY_AFTER_SECONDS = "60";
    private static final int MAX_TRACKED_KEYS = 10_000;
    private static final Set<String> WRITE_METHODS = Set.of("POST", "PATCH", "PUT", "DELETE");

    private final RateLimitProperties properties;
    private final ClientAddressResolver clientAddressResolver;
    private final ObjectMapper objectMapper;
    private final ConcurrentMap<String, Window> windows = new ConcurrentHashMap<>();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        Limit limit = limitFor(exchange);
        if (!properties.isEnabled() || limit == null) {
            return chain.filter(exchange);
        }
        return exchange.getPrincipal()
                .map(principal -> identity(principal, exchange))
                .switchIfEmpty(Mono.fromSupplier(() -> "ip:" + clientAddressResolver.resolve(exchange)))
                .flatMap(identity -> checkLimit(exchange, chain, limit, identity));
    }

    private Mono<Void> checkLimit(ServerWebExchange exchange, WebFilterChain chain, Limit limit, String identity) {
        long now = Instant.now().getEpochSecond();
        String key = limit.name() + "|" + identity;
        Window window = windows.compute(key, (ignored, current) ->
                current == null || now - current.startedAt >= WINDOW_SECONDS ? new Window(now) : current);
        purgeExpiredWindows(now);

        if (window.count.incrementAndGet() <= limit.maxPerMinute()) {
            return chain.filter(exchange);
        }
        return tooManyRequests(exchange, limit.message());
    }

    private String identity(Principal principal, ServerWebExchange exchange) {
        if (principal instanceof JwtAuthenticationToken jwtAuthentication) {
            var subject = jwtAuthentication.getToken().getSubject();
            if (StringUtils.hasText(subject)) {
                return "user:" + subject;
            }
        }
        return "ip:" + clientAddressResolver.resolve(exchange);
    }

    private Limit limitFor(ServerWebExchange exchange) {
        String method = exchange.getRequest().getMethod().name();
        String path = exchange.getRequest().getPath().value();
        if (!path.equals(REPORTS_PATH) && !path.startsWith(REPORTS_PATH + "/")) {
            return null;
        }
        if ("POST".equals(method) && path.equals(REPORTS_PATH)) {
            return new Limit("report", properties.effectiveRequestsPerMinute(),
                    "Too many security reports. Please retry later.");
        }
        if ("POST".equals(method) && path.endsWith(COMMENTS_SUFFIX)) {
            return new Limit("comment", properties.effectiveCommentsPerMinute(),
                    "Too many comments. Please retry later.");
        }
        if (WRITE_METHODS.contains(method)) {
            return new Limit("write", properties.effectiveWritesPerMinute(),
                    "Too many write requests. Please retry later.");
        }
        return null;
    }

    /** Las ventanas se descartan al cambiar de minuto; la purga evita que el mapa crezca con claves efimeras. */
    private void purgeExpiredWindows(long now) {
        if (windows.size() <= MAX_TRACKED_KEYS) {
            return;
        }
        windows.entrySet().removeIf(entry -> now - entry.getValue().startedAt >= WINDOW_SECONDS);
    }

    private Mono<Void> tooManyRequests(ServerWebExchange exchange, String message) {
        var response = exchange.getResponse();
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().set(HttpHeaders.RETRY_AFTER, RETRY_AFTER_SECONDS);
        response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        try {
            byte[] body = objectMapper.writeValueAsBytes(
                    ProblemDetails.of(HttpStatus.TOO_MANY_REQUESTS, message, exchange));
            return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
        } catch (Exception exception) {
            return Mono.error(exception);
        }
    }

    private record Limit(String name, int maxPerMinute, String message) {
    }

    private static final class Window {
        private final long startedAt;
        private final AtomicInteger count = new AtomicInteger();

        private Window(long startedAt) {
            this.startedAt = startedAt;
        }
    }
}
