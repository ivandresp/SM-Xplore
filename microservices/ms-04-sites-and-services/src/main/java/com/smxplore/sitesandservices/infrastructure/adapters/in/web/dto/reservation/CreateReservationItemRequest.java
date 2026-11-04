package com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.reservation;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateReservationItemRequest(
        @NotNull UUID serviceId,
        @Min(1) int people
) {
}
