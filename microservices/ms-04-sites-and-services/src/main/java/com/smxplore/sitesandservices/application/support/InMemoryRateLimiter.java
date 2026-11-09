package com.smxplore.sitesandservices.application.support;

import com.smxplore.sitesandservices.domain.exception.RateLimitExceededException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limitador en memoria por cuenta/token. Los limites son por instancia: con varias replicas el
 * tope efectivo se multiplica por el numero de instancias (documentado en el README del servicio).
 */
public final class InMemoryRateLimiter {

    private final int maxRequests;
    private final Duration window;
    private final ConcurrentHashMap<String, Deque<Instant>> requests = new ConcurrentHashMap<>();

    public InMemoryRateLimiter(int maxRequests, Duration window) {
        if (maxRequests < 1) {
            throw new IllegalArgumentException("maxRequests must be >= 1");
        }
        if (window == null || window.isNegative() || window.isZero()) {
            throw new IllegalArgumentException("window must be positive");
        }
        this.maxRequests = maxRequests;
        this.window = window;
    }

    public Mono<Void> acquire(String key) {
        Instant now = Instant.now();
        Deque<Instant> hits = requests.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (hits) {
            Instant cutoff = now.minus(window);
            while (!hits.isEmpty() && hits.peekFirst().isBefore(cutoff)) {
                hits.removeFirst();
            }
            if (hits.size() >= maxRequests) {
                return Mono.error(new RateLimitExceededException(
                        "Too many requests; retry later"));
            }
            hits.addLast(now);
        }
        return Mono.empty();
    }
}
