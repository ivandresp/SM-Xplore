package com.smxplore.sitesandservices.domain.model.review;

import com.smxplore.sitesandservices.domain.exception.InvalidCatalogException;
import com.smxplore.sitesandservices.domain.model.common.ItemType;

import java.util.UUID;

public record ReviewSummary(
        ItemType itemType,
        UUID itemId,
        double averageRating,
        long reviewCount
) {

    public ReviewSummary {
        validateItemType(itemType);
        validateItemId(itemId);
        validateAverageRating(averageRating);
        validateReviewCount(reviewCount);
    }

    private static void validateItemType(ItemType itemType) {
        if (itemType == null) {
            throw new InvalidCatalogException("review summary itemType is required");
        }
    }

    private static void validateItemId(UUID itemId) {
        if (itemId == null) {
            throw new InvalidCatalogException("review summary itemId is required");
        }
    }

    private static void validateAverageRating(double averageRating) {
        if (!Double.isFinite(averageRating) || averageRating < 0.0 || averageRating > 5.0) {
            throw new InvalidCatalogException("review summary averageRating must be between 0 and 5");
        }
    }

    private static void validateReviewCount(long reviewCount) {
        if (reviewCount < 0) {
            throw new InvalidCatalogException("review summary reviewCount must be >= 0");
        }
    }
}
