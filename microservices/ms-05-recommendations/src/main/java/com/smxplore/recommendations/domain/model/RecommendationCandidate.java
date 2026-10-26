package com.smxplore.recommendations.domain.model;

import java.time.Instant;
import java.util.UUID;

public record RecommendationCandidate(
        ItemType itemType,
        UUID itemId,
        String name,
        UUID categoryId,
        String categoryName,
        UUID siteId,
        Double latitude,
        Double longitude,
        Instant startsAt,
        Instant endsAt,
        Instant updatedAt,
        double ratingAvg,
        long ratingCount,
        double affinity,
        CandidateOrigin origin
) {

    public GeoLocation locationOrNull() {
        if (latitude == null || longitude == null) {
            return null;
        }
        return new GeoLocation(latitude, longitude);
    }
}
