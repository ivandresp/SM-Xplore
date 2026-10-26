package com.smxplore.recommendations.domain.model.recommendation;

import com.smxplore.recommendations.domain.exception.InvalidRecommendationException;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record Recommendation(
        UUID touristId,
        Instant generatedAt,
        RecommendationSource source,
        List<RecommendationItem> items
) {

    public Recommendation(
            UUID touristId,
            Instant generatedAt,
            RecommendationSource source,
            List<RecommendationItem> items
    ) {
        this.touristId = touristId;
        this.generatedAt = generatedAt;
        this.source = source;
        this.items = copyItems(items);
        validate();
    }

    public void validate() {
        validateTouristId();
        validateGeneratedAt();
        validateSource();
        validateItems();
    }

    private static List<RecommendationItem> copyItems(List<RecommendationItem> items) {
        if (items == null) {
            throw new InvalidRecommendationException("items are required");
        }
        if (items.stream().anyMatch(Objects::isNull)) {
            throw new InvalidRecommendationException("items cannot contain null values");
        }
        return List.copyOf(items);
    }

    private void validateTouristId() {
        if (touristId == null) {
            throw new InvalidRecommendationException("touristId is required");
        }
    }

    private void validateGeneratedAt() {
        if (generatedAt == null) {
            throw new InvalidRecommendationException("generatedAt is required");
        }
    }

    private void validateSource() {
        if (source == null) {
            throw new InvalidRecommendationException("recommendation source is required");
        }
    }

    private void validateItems() {
        if (items == null) {
            throw new InvalidRecommendationException("items are required");
        }
        if (items.stream().anyMatch(Objects::isNull)) {
            throw new InvalidRecommendationException("items cannot contain null values");
        }
    }
}
