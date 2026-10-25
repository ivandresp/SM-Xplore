package com.smxplore.activitiesevents.infrastructure.adapters.in.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ReservationItemsRequest(
        @NotEmpty @Size(max = 50) List<@Valid ReservationItemRequest> items
) {
}
