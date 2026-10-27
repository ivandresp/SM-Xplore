package com.smxplore.sitesandservices.domain.model.catalog;

import com.smxplore.sitesandservices.domain.exception.InvalidCatalogException;

import java.util.UUID;

public record CategoryFilter(
        CategoryScope scope,
        UUID parentId,
        String search,
        int page,
        int size
) {

    public CategoryFilter {
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
