package com.smxplore.recommendations.domain.model;

import java.util.UUID;

public record RecommendationQuery(
        UUID touristId,
        UUID siteId,
        GeoLocation location,
        int limit
) {
}
