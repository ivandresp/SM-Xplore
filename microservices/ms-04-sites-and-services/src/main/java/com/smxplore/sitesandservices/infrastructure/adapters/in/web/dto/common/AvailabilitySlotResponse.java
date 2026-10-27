package com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.common;

import java.time.DayOfWeek;
import java.time.LocalTime;

public record AvailabilitySlotResponse(
        DayOfWeek dayOfWeek,
        LocalTime opensAt,
        LocalTime closesAt
) {
}
