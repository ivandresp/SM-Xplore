package com.smxplore.activitiesevents.domain.model.indicator;

import com.smxplore.activitiesevents.domain.model.event.EventStatus;

import java.math.BigDecimal;
import java.util.UUID;

public record EventDemand(
        UUID eventId,
        String name,
        String category,
        EventStatus status,
        UUID siteId,
        String siteName,
        UUID zoneId,
        String zoneName,
        UUID managerId,
        long reservations,
        long people,
        BigDecimal revenue,
        double share,
        long capacityPeople,
        double occupancyRate,
        int expectedAttendance,
        double attendanceRate
) {

    public EventDemand {
        revenue = revenue == null ? BigDecimal.ZERO : revenue;
    }
}
