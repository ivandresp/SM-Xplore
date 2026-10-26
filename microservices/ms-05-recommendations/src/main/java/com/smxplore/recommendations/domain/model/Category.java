package com.smxplore.recommendations.domain.model;

import java.time.Instant;
import java.util.UUID;

public record Category(
        UUID categoryId,
        String name,
        CategoryScope scope,
        UUID parentId,
        Instant updatedAt
) {
}
