package com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.zone;

import jakarta.validation.constraints.NotBlank;

public record UpdateZoneRequest(
        @NotBlank String name,
        String type,
        String description
) {
}
