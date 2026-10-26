package com.smxplore.recommendations.infrastructure.adapters.in.web.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record PreferenceRequest(
        @NotNull UUID categoryId
) {
}
