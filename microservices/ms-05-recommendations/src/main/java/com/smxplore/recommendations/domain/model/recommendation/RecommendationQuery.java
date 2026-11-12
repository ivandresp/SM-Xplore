package com.smxplore.recommendations.domain.model.recommendation;

import com.smxplore.recommendations.domain.exception.InvalidRecommendationQueryException;
import com.smxplore.recommendations.domain.model.catalog.ItemType;
import com.smxplore.recommendations.domain.model.geo.GeoLocation;

import java.util.UUID;

/**
 * Resolved recommendation query.
 *
 * <p>{@code limit} still caps how many diversified recommendations are generated. The
 * {@code page}/{@code size} pair only paginates that generated list, which keeps the scoring and
 * diversity budgets unchanged. The filters are applied to candidates before scoring.</p>
 */
public record RecommendationQuery(
        UUID touristId,
        UUID siteId,
        GeoLocation location,
        int limit,
        int page,
        int size,
        UUID categoryId,
        ItemType itemType,
        Double minRating
) {

    public RecommendationQuery(
            UUID touristId,
            UUID siteId,
            GeoLocation location,
            int limit,
            int page,
            int size,
            UUID categoryId,
            ItemType itemType,
            Double minRating
    ) {
        this.touristId = touristId;
        this.siteId = siteId;
        this.location = location;
        this.limit = limit;
        this.page = page;
        this.size = size;
        this.categoryId = categoryId;
        this.itemType = itemType;
        this.minRating = minRating;
        validate();
    }

    public void validate() {
        validateTouristId();
        validateLimit();
        validatePage();
        validateSize();
        validateMinRating();
    }

    private void validateTouristId() {
        if (touristId == null) {
            throw new InvalidRecommendationQueryException("touristId is required");
        }
    }

    private void validateLimit() {
        if (limit < 1) {
            throw new InvalidRecommendationQueryException("limit must be >= 1");
        }
    }

    private void validatePage() {
        if (page < 0) {
            throw new InvalidRecommendationQueryException("page must be >= 0");
        }
    }

    private void validateSize() {
        if (size < 1) {
            throw new InvalidRecommendationQueryException("size must be >= 1");
        }
    }

    private void validateMinRating() {
        if (minRating == null) {
            return;
        }
        if (!Double.isFinite(minRating) || minRating < 0.0 || minRating > 5.0) {
            throw new InvalidRecommendationQueryException("minRating must be between 0 and 5");
        }
    }
}
