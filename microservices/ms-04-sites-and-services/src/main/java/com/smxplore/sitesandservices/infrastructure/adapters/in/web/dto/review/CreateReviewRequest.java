package com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.review;

import com.smxplore.sitesandservices.domain.model.common.ItemType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record CreateReviewRequest(
        @NotNull ItemType itemType,
        @NotNull UUID itemId,
        @Min(1) @Max(5) int rating,
        String commentary,
        List<String> photosUrls
) {
}
