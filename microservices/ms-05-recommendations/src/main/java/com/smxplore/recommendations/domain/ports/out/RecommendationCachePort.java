package com.smxplore.recommendations.domain.ports.out;

import com.smxplore.recommendations.domain.model.recommendation.Recommendation;
import reactor.core.publisher.Mono;

import java.util.Optional;

/**
 * Reads and writes the recommendation results that can be reused.
 *
 * <p>The cache is best effort: a missing entry, an unavailable store or a failure to store must
 * never fail the request. Implementations therefore resolve to an empty value instead of an error.
 */
public interface RecommendationCachePort {

    Mono<Optional<Recommendation>> get(String key);

    Mono<Void> put(String key, Recommendation recommendation);
}
