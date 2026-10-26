package com.smxplore.recommendations.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record Recommendation(
        UUID touristId,
        Instant generatedAt,
        RecommendationSource source,
        List<RecommendationItem> items
) {

    public Recommendation {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
