package com.smxplore.recommendations.infrastructure.adapters.out.redis;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.smxplore.recommendations.domain.model.recommendation.Recommendation;
import com.smxplore.recommendations.domain.ports.out.RecommendationCachePort;
import com.smxplore.recommendations.infrastructure.config.RecommendationCacheProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Mono;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Two layer cache: an in-process Caffeine layer in front of a Redis layer shared by every instance.
 *
 * <p>Both layers are best effort. When Redis is down the local layer keeps serving, reads go to
 * the graph and writes are dropped, so a degraded cache never turns into an unavailable API.
 */
@Slf4j
public class RedisRecommendationCacheAdapter implements RecommendationCachePort {

    private final ReactiveStringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final RecommendationCacheProperties properties;
    private final Cache<String, Recommendation> local;
    private final AtomicBoolean redisFailureLogged = new AtomicBoolean();

    public RedisRecommendationCacheAdapter(
            ReactiveStringRedisTemplate redis,
            ObjectMapper objectMapper,
            RecommendationCacheProperties properties
    ) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.local = buildLocalCache(properties);
    }

    @Override
    public Mono<Optional<Recommendation>> get(String key) {
        if (!properties.isEnabled()) {
            return Mono.just(Optional.empty());
        }
        Recommendation cached = local.getIfPresent(key);
        if (cached != null) {
            return Mono.just(Optional.of(cached));
        }
        return redis.opsForValue()
                .get(redisKey(key))
                .flatMap(value -> Mono.just(deserialize(value)))
                .doOnNext(optional -> optional.ifPresent(recommendation -> local.put(key, recommendation)))
                .switchIfEmpty(Mono.just(Optional.empty()))
                .onErrorResume(error -> {
                    warnRedisFailure("leer", error);
                    return Mono.just(Optional.empty());
                });
    }

    @Override
    public Mono<Void> put(String key, Recommendation recommendation) {
        if (!properties.isEnabled() || recommendation == null) {
            return Mono.empty();
        }
        local.put(key, recommendation);
        return Mono.defer(() -> redis.opsForValue()
                        .set(redisKey(key), serialize(recommendation), properties.getRedisTtl()))
                .then()
                .onErrorResume(error -> {
                    warnRedisFailure("escribir", error);
                    return Mono.empty();
                });
    }

    private String redisKey(String key) {
        return properties.getKeyPrefix() + key;
    }

    private Optional<Recommendation> deserialize(String value) {
        try {
            return Optional.of(objectMapper.readValue(value, Recommendation.class));
        } catch (JacksonException error) {
            log.warn("Entrada de cache ilegible, se descarta: {}", error.getOriginalMessage());
            return Optional.empty();
        }
    }

    private String serialize(Recommendation recommendation) {
        return objectMapper.writeValueAsString(recommendation);
    }

    private void warnRedisFailure(String action, Throwable error) {
        if (redisFailureLogged.compareAndSet(false, true)) {
            log.warn("No se pudo {} la cache de recomendaciones en Redis ({}); se sigue sin cache",
                    action, error.getMessage());
        }
    }

    private static Cache<String, Recommendation> buildLocalCache(RecommendationCacheProperties properties) {
        return Caffeine.newBuilder()
                .expireAfterWrite(properties.getLocalTtl())
                .maximumSize(properties.getLocalMaxSize())
                .build();
    }
}
