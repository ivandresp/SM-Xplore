package com.smxplore.recommendations.domain.model.recommendation;

import com.smxplore.recommendations.domain.exception.InvalidRecommendationQueryException;
import com.smxplore.recommendations.domain.model.geo.GeoLocation;

import java.util.UUID;

public record RecommendationQuery(
        UUID touristId,
        UUID siteId,
        GeoLocation location,
        int limit
) {

    public RecommendationQuery(UUID touristId, UUID siteId, GeoLocation location, int limit) {
        this.touristId = touristId;
        this.siteId = siteId;
        this.location = location;
        this.limit = limit;
        validate();
    }

    public void validate() {
        validateTouristId();
        validateLimit();
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
}
