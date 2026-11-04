package com.smxplore.recommendations.domain.model.recommendation;

import com.smxplore.recommendations.domain.exception.InvalidLocationException;
import com.smxplore.recommendations.domain.exception.InvalidRecommendationQueryException;
import com.smxplore.recommendations.domain.model.geo.GeoLocation;
import lombok.Builder;

import java.util.UUID;

/**
 * Input command for the recommendation use case. It carries the authenticated
 * caller and the raw request parameters; the application layer resolves the
 * access policy and builds a {@link RecommendationQuery}.
 */
@Builder
public record RecommendationRequest(
        UUID requesterId,
        boolean admin,
        UUID requestedTouristId,
        UUID siteId,
        Double latitude,
        Double longitude,
        int limit
) {

    public void validate() {
        validateRequester();
        validateLimit();
        validateLocation();
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
}
