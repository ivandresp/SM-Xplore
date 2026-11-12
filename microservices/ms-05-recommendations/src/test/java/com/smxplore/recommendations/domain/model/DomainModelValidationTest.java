package com.smxplore.recommendations.domain.model;

import com.smxplore.recommendations.domain.exception.InvalidLocationException;
import com.smxplore.recommendations.domain.exception.InvalidPreferenceException;
import com.smxplore.recommendations.domain.exception.InvalidRecommendationException;
import com.smxplore.recommendations.domain.exception.InvalidRecommendationPolicyException;
import com.smxplore.recommendations.domain.exception.InvalidRecommendationQueryException;
import com.smxplore.recommendations.domain.exception.InvalidScoringException;
import com.smxplore.recommendations.domain.model.catalog.ItemType;
import com.smxplore.recommendations.domain.model.preference.Preference;
import com.smxplore.recommendations.domain.model.preference.PreferenceSource;
import com.smxplore.recommendations.domain.model.ranking.RecommendationPolicy;
import com.smxplore.recommendations.domain.model.ranking.ScoredCandidate;
import com.smxplore.recommendations.domain.model.recommendation.CandidateOrigin;
import com.smxplore.recommendations.domain.model.recommendation.RecommendationCandidate;
import com.smxplore.recommendations.domain.model.recommendation.RecommendationCacheKey;
import com.smxplore.recommendations.domain.model.recommendation.RecommendationItem;
import com.smxplore.recommendations.domain.model.recommendation.RecommendationQuery;
import com.smxplore.recommendations.domain.model.recommendation.RecommendationRequest;
import com.smxplore.recommendations.domain.model.recommendation.RecommendationSignals;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

class DomainModelValidationTest {

    private static final UUID ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-10-26T12:00:00Z");

    @Test
    void rejectsInvalidPolicyWeights() {
        assertThatThrownBy(() -> {
            RecommendationPolicy policy = RecommendationPolicy.builder()
                    .defaultLimit(20)
                    .maxLimit(50)
                    .candidateLimit(200)
                    .categoryMaxDepth(2)
                    .proximityRadiusKm(10.0)
                    .noveltyHalfLifeDays(90.0)
                    .preferenceHalfLifeDays(90.0)
                    .qualityPriorMean(3.5)
                    .qualityPriorCount(10.0)
                    .maxItemsPerSite(2)
                    .maxCategoryRatio(0.4)
                    .affinityWeight(0.0)
                    .qualityWeight(0.0)
                    .proximityWeight(0.0)
                    .noveltyWeight(0.0)
                    .build();
            policy.validate();
        }).isInstanceOf(InvalidRecommendationPolicyException.class);
    }

    @Test
    void rejectsQueryWithoutTourist() {
        assertThatThrownBy(() -> new RecommendationQuery(null, null, null, 20, 0, 20, null, null, null))
                .isInstanceOf(InvalidRecommendationQueryException.class);
    }

    @Test
    void rejectsQueryWithInvalidLimit() {
        assertThatThrownBy(() -> new RecommendationQuery(ID, null, null, 0, 0, 20, null, null, null))
                .isInstanceOf(InvalidRecommendationQueryException.class);
    }

    @Test
    void rejectsQueryWithNegativePage() {
        assertThatThrownBy(() -> new RecommendationQuery(ID, null, null, 20, -1, 20, null, null, null))
                .isInstanceOf(InvalidRecommendationQueryException.class);
    }

    @Test
    void rejectsQueryWithZeroSize() {
        assertThatThrownBy(() -> new RecommendationQuery(ID, null, null, 20, 0, 0, null, null, null))
                .isInstanceOf(InvalidRecommendationQueryException.class);
    }

    @Test
    void rejectsQueryWithMinRatingOutsideTheCatalogRange() {
        assertThatThrownBy(() -> new RecommendationQuery(ID, null, null, 20, 0, 20, null, null, 5.5))
                .isInstanceOf(InvalidRecommendationQueryException.class);
    }

    @Test
    void rejectsCacheKeyWithHalfALocation() {
        assertThatThrownBy(() -> new RecommendationCacheKey(ID, null, 10.0, null, 20, 0, 20, null, null, null))
                .isInstanceOf(InvalidRecommendationException.class);
    }

    @Test
    void normalizesEquivalentRequestsUnderTheSameCacheKey() {
        RecommendationCacheKey first = new RecommendationCacheKey(
                ID, null, 10.39104, -75.47904, 20, 1, 10, null, ItemType.SITE, 4.0);
        RecommendationCacheKey second = new RecommendationCacheKey(
                ID, null, 10.39101, -75.47901, 20, 1, 10, null, ItemType.SITE, 4.001);

        assertThat(first.value(7)).isEqualTo(second.value(7));
        assertThat(first.value(8)).isNotEqualTo(second.value(7));
    }

    @Test
    void rejectsItemWithScoreOutsideRange() {
        RecommendationSignals signals = RecommendationSignals.builder()
                .affinity(0.5)
                .quality(0.5)
                .distanceKm(null)
                .novelty(0.5)
                .build();
        signals.validate();

        assertThatThrownBy(() -> {
            RecommendationItem item = RecommendationItem.builder()
                    .itemType(ItemType.EVENT)
                    .itemId(ID)
                    .name("Event")
                    .categoryId(ID)
                    .siteId(ID)
                    .score(1.5)
                    .reason("reason")
                    .signals(signals)
                    .build();
            item.validate();
        }).isInstanceOf(InvalidRecommendationException.class);
    }

    @Test
    void rejectsPreferenceWithNonPositiveWeight() {
        assertThatThrownBy(() -> {
            Preference preference = Preference.builder()
                    .touristId(ID)
                    .categoryId(ID)
                    .weight(0.0)
                    .source(PreferenceSource.EXPLICIT)
                    .updatedAt(NOW)
                    .build();
            preference.validate();
        }).isInstanceOf(InvalidPreferenceException.class);
    }

    @Test
    void rejectsRequestWithOnlyOneCoordinate() {
        assertThatThrownBy(() -> {
            RecommendationRequest request = RecommendationRequest.builder()
                    .requesterId(ID)
                    .latitude(10.0)
                    .limit(20)
                    .build();
            request.validate();
        }).isInstanceOf(InvalidLocationException.class);
    }

    @Test
    void rejectsCandidateWithInvalidCoordinates() {
        assertThatThrownBy(() -> {
            RecommendationCandidate candidate = RecommendationCandidate.builder()
                    .itemType(ItemType.EVENT)
                    .itemId(ID)
                    .name("Event")
                    .categoryId(ID)
                    .categoryName("Música")
                    .siteId(ID)
                    .latitude(91.0)
                    .longitude(-75.0)
                    .updatedAt(NOW)
                    .ratingAvg(4.5)
                    .ratingCount(10)
                    .affinity(1.0)
                    .origin(CandidateOrigin.AFFINITY)
                    .build();
            candidate.validate();
        }).isInstanceOf(InvalidRecommendationException.class);
    }

    @Test
    void rejectsScoredCandidateWithInvalidScore() {
        RecommendationCandidate candidate = RecommendationCandidate.builder()
                .itemType(ItemType.EVENT)
                .itemId(ID)
                .name("Event")
                .categoryId(ID)
                .categoryName("Música")
                .siteId(ID)
                .latitude(10.0)
                .longitude(-75.0)
                .updatedAt(NOW)
                .ratingAvg(4.5)
                .ratingCount(10)
                .affinity(1.0)
                .origin(CandidateOrigin.AFFINITY)
                .build();
        candidate.validate();

        RecommendationSignals signals = RecommendationSignals.builder()
                .affinity(0.5)
                .quality(0.5)
                .distanceKm(null)
                .novelty(0.5)
                .build();
        signals.validate();

        assertThatThrownBy(() -> new ScoredCandidate(candidate, 2.0, signals))
                .isInstanceOf(InvalidScoringException.class);
    }
}
