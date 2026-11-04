package com.smxplore.securityreports.infrastructure.adapters.in.web.security;

import com.smxplore.securityreports.infrastructure.adapters.in.web.error.ProblemDetails;
import com.smxplore.securityreports.infrastructure.config.RateLimitProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@RequiredArgsConstructor
public class ReportRateLimitWebFilter implements WebFilter {

    private static final String REPORTS_PATH = "/api/v1/security-reports";

    private final RateLimitProperties properties;
    private final ObjectMapper objectMapper;
    private final ConcurrentMap<String, Window> windows = new ConcurrentHashMap<>();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!properties.isEnabled() || !isReportCreation(exchange)) {
            return chain.filter(exchange);
        }

        String key = exchange.getRequest().getRemoteAddress() == null
                ? "unknown"
                : exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
        long now = Instant.now().getEpochSecond();
        Window window = windows.compute(key, (ignored, current) ->
                current == null || now - current.startedAt >= 60
                        ? new Window(now)
                        : current);

        if (window.count.incrementAndGet() > Math.max(1, properties.getRequestsPerMinute())) {
            exchange.getResponse().getHeaders().set("Retry-After", "60");
            exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
            byte[] body;
            try {
                body = objectMapper.writeValueAsBytes(ProblemDetails.of(
                        HttpStatus.TOO_MANY_REQUESTS,
                        "Too many security reports. Please retry later.",
                        exchange));
            } catch (Exception exception) {
                return Mono.error(exception);
            }
            exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            return exchange.getResponse().writeWith(Mono.just(
                    exchange.getResponse().bufferFactory().wrap(body)));
        }
        return chain.filter(exchange);
    }

    private static boolean isReportCreation(ServerWebExchange exchange) {
        return "POST".equalsIgnoreCase(exchange.getRequest().getMethod().name())
                && REPORTS_PATH.equals(exchange.getRequest().getPath().value());
    }

    private static final class Window {
        private final long startedAt;
        private final AtomicInteger count = new AtomicInteger();

        private Window(long startedAt) {
            this.startedAt = startedAt;
        }
    }
}
