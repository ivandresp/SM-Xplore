package com.smxplore.users.infrastructure.adapter.out.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.smxplore.users.application.port.out.AccountAttemptLimiter;
import com.smxplore.users.domain.exception.TooManyLoginAttemptsException;
import com.smxplore.users.infrastructure.config.RateLimitProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import reactor.core.publisher.Mono;

/**
 * Contador de intentos fallidos por cuenta en memoria de la instancia. Complementa al límite por IP del filtro de
 * autenticación: un atacante que rota direcciones sigue topando con el cupo de la cuenta.
 */
public final class InMemoryAccountAttemptLimiter implements AccountAttemptLimiter {

    private final Cache<String, Attempts> attempts;
    private final int maxAttempts;
    private final Duration window;
    private final Clock clock;

    public InMemoryAccountAttemptLimiter(RateLimitProperties properties, Clock clock) {
        this.maxAttempts = properties.accountMaxAttempts();
        this.window = properties.accountWindow();
        this.clock = clock;
        this.attempts = Caffeine.newBuilder()
                .expireAfterAccess(window.multipliedBy(2))
                .maximumSize(10_000)
                .build();
    }

    @Override
    public Mono<Void> ensureAllowed(String key) {
        return Mono.fromRunnable(() -> {
            Attempts state = attempts.getIfPresent(key);
            if (state == null) {
                return;
            }
            Instant now = clock.instant();
            synchronized (state) {
                if (state.expired(now, window)) {
                    attempts.invalidate(key);
                    return;
                }
                if (state.count.get() >= maxAttempts) {
                    throw new TooManyLoginAttemptsException(retryAfter(now, state.start));
                }
            }
        });
    }

    @Override
    public Mono<Void> recordFailure(String key) {
        return Mono.fromRunnable(() -> {
            Instant now = clock.instant();
            Attempts state = attempts.get(key, ignored -> new Attempts(now));
            synchronized (state) {
                if (state.expired(now, window)) {
                    state.start = now;
                    state.count.set(0);
                }
                state.count.incrementAndGet();
            }
        });
    }

    @Override
    public Mono<Void> clear(String key) {
        return Mono.fromRunnable(() -> attempts.invalidate(key));
    }

    private Duration retryAfter(Instant now, Instant start) {
        long elapsed = Math.max(0, Duration.between(start, now).toSeconds());
        return Duration.ofSeconds(Math.max(1, window.toSeconds() - elapsed));
    }

    private static final class Attempts {

        private Instant start;
        private final AtomicInteger count = new AtomicInteger();

        private Attempts(Instant start) {
            this.start = start;
        }

        private boolean expired(Instant now, Duration window) {
            return !now.isBefore(start.plus(window));
        }
    }
}
