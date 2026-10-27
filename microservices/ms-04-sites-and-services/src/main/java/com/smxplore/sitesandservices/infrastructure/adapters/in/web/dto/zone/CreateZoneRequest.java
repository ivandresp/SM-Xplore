package com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.zone;

import com.smxplore.sitesandservices.domain.model.zone.ZoneStatus;
import jakarta.validation.constraints.NotBlank;

public record CreateZoneRequest(
        @NotBlank String name,
        String type,
        String description,
        ZoneStatus status
) {
}
