package com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.category;

import com.smxplore.sitesandservices.domain.model.catalog.CategoryScope;

import java.time.Instant;
import java.util.UUID;

public record CategoryResponse(
        UUID categoryId,
        String name,
        CategoryScope scope,
        UUID parentId,
        Instant updatedAt
) {
}
