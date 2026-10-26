package com.smxplore.recommendations.domain.model.event;

import com.smxplore.recommendations.domain.exception.InvalidEventException;
import com.smxplore.recommendations.domain.model.catalog.CategoryScope;

import java.time.Instant;
import java.util.UUID;

public record CategoryChangedEvent(
        EventMetadata metadata,
        UUID categoryId,
        String name,
        CategoryScope scope,
        UUID parentId,
        Instant updatedAt
) implements ProjectedEvent {

    public CategoryChangedEvent {
        EventValidation.metadata(metadata);
        EventValidation.uuid(categoryId, "categoryId");
        EventValidation.text(name, "category name");
        EventValidation.required(scope, "scope");
        EventValidation.instant(updatedAt, "updatedAt");
        if (categoryId.equals(parentId)) {
            throw new InvalidEventException("a category cannot be its own parent");
        }
    }
}
