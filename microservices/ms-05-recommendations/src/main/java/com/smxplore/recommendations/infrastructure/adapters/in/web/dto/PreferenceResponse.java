package com.smxplore.recommendations.infrastructure.adapters.in.web.dto;

import com.smxplore.recommendations.domain.model.PreferenceSource;

import java.time.Instant;
import java.util.UUID;

public record PreferenceResponse(
        UUID touristId,
        UUID categoryId,
        double weight,
        PreferenceSource source,
        Instant updatedAt
) {
}
