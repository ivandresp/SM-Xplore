package com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.site;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AssignSiteZoneRequest(
        @NotNull UUID zoneId
) {
}
