package com.smxplore.recommendations.domain.model.recommendation;

import com.smxplore.recommendations.domain.exception.InvalidRecommendationException;
import com.smxplore.recommendations.domain.model.catalog.ItemType;
import lombok.Builder;

import java.util.UUID;

@Builder
public record RecommendationItem(
        ItemType itemType,
        UUID itemId,
        String name,
        UUID categoryId,
        UUID siteId,
        double score,
        String reason,
        RecommendationSignals signals
) {

    public void validate() {
        validateItemType();
        validateItemId();
        validateName();
        validateCategoryId();
        validateSiteId();
        validateScore();
        validateReason();
        validateSignals();
    }

    private void validateItemType() {
        if (itemType == null) {
            throw new InvalidRecommendationException("itemType is required");
        }
    }

    private void validateItemId() {
        if (itemId == null) {
            throw new InvalidRecommendationException("itemId is required");
        }
    }

    private void validateName() {
        if (name == null || name.isBlank()) {
            throw new InvalidRecommendationException("item name is required");
        }
    }

    private void validateCategoryId() {
        if (categoryId == null) {
            throw new InvalidRecommendationException("categoryId is required");
        }
    }

    private void validateSiteId() {
        if (siteId == null) {
            throw new InvalidRecommendationException("siteId is required");
        }
    }

    private void validateScore() {
        if (!Double.isFinite(score) || score < 0.0 || score > 1.0) {
            throw new InvalidRecommendationException("score must be between 0 and 1");
        }
    }

    private void validateReason() {
        if (reason == null || reason.isBlank()) {
            throw new InvalidRecommendationException("recommendation reason is required");
        }
    }

    private void validateSignals() {
        if (signals == null) {
            throw new InvalidRecommendationException("signals are required");
        }
    }
}
