package com.smxplore.activitiesevents.infrastructure.adapters.in.web.dto;

import com.smxplore.activitiesevents.domain.model.event.EventStatus;
import jakarta.validation.constraints.NotNull;

public record ChangeEventStatusRequest(
        @NotNull EventStatus status
) {
}
