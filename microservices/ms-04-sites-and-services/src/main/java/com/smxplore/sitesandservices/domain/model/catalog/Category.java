package com.smxplore.sitesandservices.domain.model.catalog;

import com.smxplore.sitesandservices.domain.exception.InvalidCatalogException;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.time.Instant;
import java.util.UUID;

@Getter
@Accessors(fluent = true)
@Builder(toBuilder = true)
public final class Category {

    private final UUID categoryId;
    private final String name;
    @Builder.Default
    private final CategoryScope scope = CategoryScope.GENERAL;
    private final UUID parentId;
    private final Instant updatedAt;

    public void validate() {
        validateCategoryId(categoryId);
        validateName(name);
        validateScope(scope);
        validateParent(categoryId, parentId);
        validateUpdatedAt(updatedAt);
    }

    private static void validateCategoryId(UUID categoryId) {
        if (categoryId == null) {
            throw new InvalidCatalogException("categoryId is required");
        }
    }

    private static void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new InvalidCatalogException("category name is required");
        }
    }

    private static void validateScope(CategoryScope scope) {
        if (scope == null) {
            throw new InvalidCatalogException("category scope is required");
        }
    }

    private static void validateParent(UUID categoryId, UUID parentId) {
        if (categoryId != null && categoryId.equals(parentId)) {
            throw new InvalidCatalogException("a category cannot be its own parent");
        }
    }

    private static void validateUpdatedAt(Instant updatedAt) {
        if (updatedAt == null) {
            throw new InvalidCatalogException("category updatedAt is required");
        }
    }
}
