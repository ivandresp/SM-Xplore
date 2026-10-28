package com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.reservation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public record CreateReservationRequest(
        @NotNull Instant bookedFor,
        @Size(max = 1000) String notes,
        @Valid @NotEmpty @Size(max = 10) List<CreateReservationItemRequest> items
) {
}
