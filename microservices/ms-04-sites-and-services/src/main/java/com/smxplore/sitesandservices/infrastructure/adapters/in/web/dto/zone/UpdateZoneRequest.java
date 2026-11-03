package com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.zone;

import com.smxplore.sitesandservices.domain.model.zone.Zone;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateZoneRequest(
        @NotBlank @Size(max = Zone.MAX_NAME_LENGTH) String name,
        @Size(max = Zone.MAX_TYPE_LENGTH) String type,
        String description
) {
}
