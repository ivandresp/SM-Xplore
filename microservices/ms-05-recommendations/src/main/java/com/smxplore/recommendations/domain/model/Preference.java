package com.smxplore.recommendations.domain.model;

import java.time.Instant;
import java.util.UUID;

public record Preference(
        UUID touristId,
        UUID categoryId,
        double weight,
        PreferenceSource source,
        Instant updatedAt
) {
}
