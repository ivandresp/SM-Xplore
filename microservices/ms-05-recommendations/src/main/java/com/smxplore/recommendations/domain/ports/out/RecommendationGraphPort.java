package com.smxplore.recommendations.domain.ports.out;

import com.smxplore.recommendations.domain.model.geo.GeoLocation;
import com.smxplore.recommendations.domain.model.catalog.ItemType;
import com.smxplore.recommendations.domain.model.recommendation.RecommendationCandidate;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

public interface RecommendationGraphPort {

    Flux<RecommendationCandidate> findCandidatesForTourist(
            UUID touristId,
            UUID siteId,
            GeoLocation location,
            int limit,
            Instant now
    );

    Flux<RecommendationCandidate> findFallbackCandidates(
            UUID touristId,
            UUID siteId,
            GeoLocation location,
            int limit,
            Instant now
    );

    Mono<Boolean> upsertPreference(UUID touristId, UUID categoryId, String language, Instant now);

    Mono<Void> deletePreference(UUID touristId, UUID categoryId);

    Mono<Boolean> recordView(UUID touristId, ItemType itemType, UUID itemId, String language, Instant now);
}
