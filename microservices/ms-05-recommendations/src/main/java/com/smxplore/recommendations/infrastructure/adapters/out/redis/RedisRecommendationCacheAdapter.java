package com.smxplore.recommendations.infrastructure.adapters.out.redis;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.smxplore.recommendations.domain.exception.InvalidRecommendationException;
import com.smxplore.recommendations.domain.model.recommendation.Recommendation;
import com.smxplore.recommendations.domain.model.recommendation.RecommendationItem;
import com.smxplore.recommendations.domain.ports.out.RecommendationCachePort;
import com.smxplore.recommendations.infrastructure.config.RecommendationCacheProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Mono;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
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

    /** Any entry larger than this is not a recommendation payload and is discarded (MS05-05). */
    private static final int MAX_CACHED_JSON_LENGTH = 64 * 1024;
    private static final int MAX_CACHED_ITEMS = 50;

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
                .flatMap(value -> Mono.just(deserialize(value, key)))
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

    private Optional<Recommendation> deserialize(String value, String key) {
        if (value == null || value.length() > MAX_CACHED_JSON_LENGTH) {
            log.warn("Entrada de cache demasiado grande o vacia, se descarta (clave {})", key);
            return Optional.empty();
        }
        try {
            Recommendation recommendation = objectMapper.readValue(value, Recommendation.class);
            if (!isValid(recommendation, key)) {
                log.warn("Entrada de cache incoherente, se descarta (clave {})", key);
                return Optional.empty();
            }
            return Optional.of(recommendation);
        } catch (JacksonException error) {
            log.warn("Entrada de cache ilegible, se descarta: {}", error.getOriginalMessage());
            return Optional.empty();
        } catch (InvalidRecommendationException error) {
            log.warn("Entrada de cache invalida, se descarta: {}", error.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Defensive validation of a cached payload (MS05-05). Redis is shared with other services, so
     * an entry that does not describe the tourist of the key, or that carries scores outside the
     * domain, must never reach the API response.
     */
    private boolean isValid(Recommendation recommendation, String key) {
        if (recommendation == null || recommendation.touristId() == null) {
            return false;
        }
        if (!key.contains(recommendation.touristId().toString())) {
            return false;
        }
        List<RecommendationItem> items = recommendation.items();
        if (items == null || items.size() > MAX_CACHED_ITEMS) {
            return false;
        }
        return items.stream().allMatch(item -> item != null
                && Double.isFinite(item.score())
                && item.score() >= 0.0
                && item.score() <= 1.0);
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
