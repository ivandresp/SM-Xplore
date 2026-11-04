package com.smxplore.recommendations.infrastructure.adapters.in.web.dto;

import java.util.UUID;

public record PreferenceRequest(
        UUID categoryId
) {
}
