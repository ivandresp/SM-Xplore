package com.smxplore.sitesandservices.domain.model.review;

import com.smxplore.sitesandservices.domain.exception.InvalidCatalogException;
import com.smxplore.sitesandservices.domain.model.common.ItemType;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ReputationMetrics(
        ItemType itemType,
        UUID itemId,
        double averageRating,
        long reviewCount,
        long responseCount,
        long hiddenCount,
        Map<Integer, Long> ratingDistribution,
        Instant lastReviewAt
) {

    public ReputationMetrics {
        requireNonNull(itemType, "reputation itemType is required");
        requireNonNull(itemId, "reputation itemId is required");
        if (!Double.isFinite(averageRating) || averageRating < 0.0 || averageRating > 5.0) {
            throw new InvalidCatalogException("reputation averageRating must be between 0 and 5");
        }
        requireNonNegative(reviewCount, "reputation reviewCount");
        requireNonNegative(responseCount, "reputation responseCount");
        requireNonNegative(hiddenCount, "reputation hiddenCount");
        validateDistribution(ratingDistribution);
        ratingDistribution = ratingDistribution == null ? Map.of() : Map.copyOf(ratingDistribution);
    }

    public double responseRate() {
        return reviewCount == 0 ? 0.0 : (double) responseCount / (double) reviewCount;
    }

    private static void validateDistribution(Map<Integer, Long> ratingDistribution) {
        if (ratingDistribution == null) {
            return;
        }
        ratingDistribution.forEach((rating, count) -> {
            if (rating == null || rating < 1 || rating > 5) {
                throw new InvalidCatalogException("reputation ratingDistribution keys must be between 1 and 5");
            }
            if (count == null || count < 0) {
                throw new InvalidCatalogException("reputation ratingDistribution values must be >= 0");
            }
        });
    }

    private static void requireNonNegative(long value, String field) {
        if (value < 0) {
            throw new InvalidCatalogException(field + " must be >= 0");
        }
    }

    private static void requireNonNull(Object value, String message) {
        if (value == null) {
            throw new InvalidCatalogException(message);
        }
    }
}
