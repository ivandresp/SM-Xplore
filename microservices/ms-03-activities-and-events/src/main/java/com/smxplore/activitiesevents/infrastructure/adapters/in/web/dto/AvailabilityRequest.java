package com.smxplore.activitiesevents.infrastructure.adapters.in.web.dto;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record AvailabilityRequest(
        @NotNull Instant startsAt,
        @NotNull Instant endsAt
) {
}
