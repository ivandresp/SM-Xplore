package com.smxplore.recommendations.domain.model;

import java.time.Instant;
import java.util.UUID;

public record TouristProfile(
        UUID touristId,
        String language,
        Instant updatedAt
) {
}
