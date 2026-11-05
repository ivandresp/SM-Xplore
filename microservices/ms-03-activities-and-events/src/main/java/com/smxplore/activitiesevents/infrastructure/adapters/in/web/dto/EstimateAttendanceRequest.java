package com.smxplore.activitiesevents.infrastructure.adapters.in.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record EstimateAttendanceRequest(
        @NotNull @Min(0) Integer expectedAttendance
) {
}
