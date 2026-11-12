package com.smxplore.recommendations.domain.model.recommendation;

import com.smxplore.recommendations.domain.exception.InvalidLocationException;
import com.smxplore.recommendations.domain.exception.InvalidRecommendationQueryException;
import com.smxplore.recommendations.domain.model.catalog.ItemType;
import com.smxplore.recommendations.domain.model.geo.GeoLocation;
import lombok.Builder;

import java.util.UUID;

/**
 * Input command for the recommendation use case. It carries the authenticated
 * caller and the raw request parameters; the application layer resolves the
 * access policy and builds a {@link RecommendationQuery}.
 *
 * <p>MS05-11 adds the pagination window ({@code page}/{@code size}) and the candidate filters
 * ({@code categoryId}/{@code itemType}/{@code minRating}). The filters narrow the candidate pool
 * before scoring, while the pagination window only slices the already diversified result.</p>
 */
@Builder
public record RecommendationRequest(
        UUID requesterId,
        boolean admin,
        UUID requestedTouristId,
        UUID siteId,
        Double latitude,
        Double longitude,
        int limit,
        int page,
        Integer size,
        UUID categoryId,
        ItemType itemType,
        Double minRating
) {

    public void validate() {
        validateRequester();
        validateLimit();
        validateLocation();
        validatePage();
        validateSize();
        validateMinRating();
    }

    public boolean hasLocation() {
        return latitude != null;
    }

    private void validateRequester() {
        if (requesterId == null) {
            throw new InvalidRecommendationQueryException("requesterId is required");
        }
    }

    private void validateLimit() {
        if (limit < 1) {
            throw new InvalidRecommendationQueryException("limit must be >= 1");
        }
    }

    private void validateLocation() {
        if (latitude == null && longitude == null) {
            return;
        }
        if (latitude == null || longitude == null) {
            throw new InvalidLocationException("lat and lng must be provided together");
        }
        new GeoLocation(latitude, longitude);
    }

    private void validatePage() {
        if (page < 0) {
            throw new InvalidRecommendationQueryException("page must be >= 0");
        }
    }

    private void validateSize() {
        if (size != null && size < 1) {
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
