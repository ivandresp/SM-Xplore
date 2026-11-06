package com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.common;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.DayOfWeek;
import java.time.LocalTime;

/**
 * {@code capacity} es opcional: cuando se informa, limita el cupo de ese turno además de
 * la capacidad diaria.
 */
public record AvailabilitySlotRequest(
        @NotNull DayOfWeek dayOfWeek,
        @NotNull LocalTime opensAt,
        @NotNull LocalTime closesAt,
        @Min(1) Integer capacity
) {
}
