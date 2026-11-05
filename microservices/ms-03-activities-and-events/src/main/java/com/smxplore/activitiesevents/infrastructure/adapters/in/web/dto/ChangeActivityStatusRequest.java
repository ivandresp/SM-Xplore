package com.smxplore.activitiesevents.infrastructure.adapters.in.web.dto;

import com.smxplore.activitiesevents.domain.model.activity.ActivityStatus;
import jakarta.validation.constraints.NotNull;

public record ChangeActivityStatusRequest(
        @NotNull ActivityStatus status
) {
}
