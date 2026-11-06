package com.smxplore.recommendations.infrastructure.adapters.out.redis;

import com.smxplore.recommendations.domain.ports.out.GraphVersionPort;
import com.smxplore.recommendations.infrastructure.config.RecommendationCacheProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Mono;

/**
 * Keeps the graph version in Redis so every instance shares the same invalidation point.
 *
 * <p>Redis is a coordination hint, not a dependency: if it is unavailable the version resolves to
 * zero and the cache still expires by time to live.
 */
@Slf4j
@RequiredArgsConstructor
public class RedisGraphVersionAdapter implements GraphVersionPort {

    private final ReactiveStringRedisTemplate redis;
    private final RecommendationCacheProperties properties;

    @Override
    public Mono<Long> current() {
        if (!properties.isEnabled()) {
            return Mono.just(0L);
        }
        return redis.opsForValue()
                .get(properties.getVersionKey())
                .map(RedisGraphVersionAdapter::parse)
                .defaultIfEmpty(0L)
                .onErrorResume(error -> {
                    log.warn("No se pudo leer la version del grafo en Redis: {}", error.getMessage());
                    return Mono.just(0L);
                });
    }

    @Override
    public Mono<Void> bump() {
        if (!properties.isEnabled()) {
            return Mono.empty();
        }
        return redis.opsForValue()
                .increment(properties.getVersionKey())
                .then()
                .onErrorResume(error -> {
                    log.warn("No se pudo incrementar la version del grafo en Redis: {}", error.getMessage());
                    return Mono.empty();
                });
    }

    private static Long parse(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException error) {
            return 0L;
        }
    }
}
