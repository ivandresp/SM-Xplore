package com.smxplore.activitiesevents.infrastructure.adapters.in.web.dto;

import com.smxplore.activitiesevents.domain.model.review.ReviewItemType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateReviewRequest(
        @NotNull ReviewItemType itemType,
        @NotNull UUID itemId,
        @Min(1) @Max(5) int rating,
        @Size(max = 1000) String comment
) {
}
