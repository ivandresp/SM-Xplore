package com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.category;

import com.smxplore.sitesandservices.domain.model.catalog.CategoryScope;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateCategoryRequest(
        @NotBlank String name,
        @NotNull CategoryScope scope,
        UUID parentId
) {
}
