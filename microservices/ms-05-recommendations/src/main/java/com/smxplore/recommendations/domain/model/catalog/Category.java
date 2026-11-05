package com.smxplore.recommendations.domain.model.catalog;

import com.smxplore.recommendations.domain.exception.InvalidCatalogException;

import java.time.Instant;
import java.util.UUID;

public record Category(
        UUID categoryId,
        String name,
        CategoryScope scope,
        UUID parentId,
        Instant updatedAt
) {

    public Category(UUID categoryId, String name, CategoryScope scope, UUID parentId, Instant updatedAt) {
        this.categoryId = categoryId;
        this.name = name;
        this.scope = scope;
        this.parentId = parentId;
        this.updatedAt = updatedAt;
        validate();
    }

    public void validate() {
        validateCategoryId();
        validateName();
        validateScope();
        validateParent();
        validateUpdatedAt();
    }

    private void validateCategoryId() {
        if (categoryId == null) {
            throw new InvalidCatalogException("categoryId is required");
        }
    }

    private void validateName() {
        if (name == null || name.isBlank()) {
            throw new InvalidCatalogException("category name is required");
        }
    }

    private void validateScope() {
        if (scope == null) {
            throw new InvalidCatalogException("category scope is required");
        }
    }

    private void validateParent() {
        if (categoryId != null && categoryId.equals(parentId)) {
            throw new InvalidCatalogException("a category cannot be its own parent");
        }
    }

    private void validateUpdatedAt() {
        if (updatedAt == null) {
            throw new InvalidCatalogException("category updatedAt is required");
        }
    }
}
