package com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.zone;

import com.smxplore.sitesandservices.domain.model.zone.ZoneStatus;
import jakarta.validation.constraints.NotNull;

public record ChangeZoneStatusRequest(
        @NotNull ZoneStatus status
) {
}
