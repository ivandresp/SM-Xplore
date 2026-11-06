package com.smxplore.activitiesevents.domain.model.indicator;

import java.math.BigDecimal;
import java.util.UUID;

public record ActivityDemand(
        UUID activityId,
        String name,
        String category,
        UUID siteId,
        String siteName,
        UUID zoneId,
        String zoneName,
        UUID guideId,
        long reservations,
        long people,
        BigDecimal revenue,
        double share,
        long capacityPeople,
        double occupancyRate
) {

    public ActivityDemand {
        revenue = revenue == null ? BigDecimal.ZERO : revenue;
    }
}
