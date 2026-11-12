package com.smxplore.recommendations.domain.ports.out;

import com.smxplore.recommendations.domain.model.geo.GeoLocation;
import com.smxplore.recommendations.domain.model.catalog.ItemType;
import com.smxplore.recommendations.domain.model.preference.Preference;
import com.smxplore.recommendations.domain.model.recommendation.RecommendationCandidate;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
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

    /**
     * Replaces every explicit preference of the tourist with {@code categoryIds} in one graph
     * transaction (MS05-11). Inferred preferences are kept. The returned list is the resulting
     * set of preferences after the replacement, ordered like
     * {@link #findPreferences(UUID)}.
     *
     * <p>If one of the ids does not exist, the transaction fails with
     * {@link com.smxplore.recommendations.domain.exception.CategoryNotFoundException} and no edge
     * is modified.</p>
     */
    Mono<List<Preference>> replacePreferences(
            UUID touristId,
            List<UUID> categoryIds,
            String language,
            Instant now
    );

    Mono<Void> deletePreference(UUID touristId, UUID categoryId);

    Flux<Preference> findPreferences(UUID touristId);

    Mono<Boolean> recordView(UUID touristId, ItemType itemType, UUID itemId, String language, Instant now);
}
