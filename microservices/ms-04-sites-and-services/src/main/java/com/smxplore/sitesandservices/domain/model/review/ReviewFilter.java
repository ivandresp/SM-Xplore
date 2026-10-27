package com.smxplore.sitesandservices.domain.model.review;

import com.smxplore.sitesandservices.domain.exception.InvalidCatalogException;
import com.smxplore.sitesandservices.domain.model.common.ItemType;

import java.util.UUID;

public record ReviewFilter(
        ItemType itemType,
        UUID itemId,
        UUID touristId,
        ReviewStatus status,
        int page,
        int size
) {

    public ReviewFilter {
        validatePage(page);
        validateSize(size);
    }

    private static void validatePage(int page) {
        if (page < 0) {
            throw new InvalidCatalogException("page must be >= 0");
        }
    }

    private static void validateSize(int size) {
        if (size < 1 || size > 100) {
            throw new InvalidCatalogException("size must be between 1 and 100");
        }
    }
}
