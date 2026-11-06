package com.smxplore.sitesandservices.domain.model.indicator;

import java.math.BigDecimal;
import java.util.UUID;

public record ZoneDemand(
        UUID zoneId,
        String zoneName,
        String zoneStatus,
        long reservations,
        long people,
        BigDecimal revenue,
        double share,
        long capacityPeople,
        double occupancyRate
) {

    public ZoneDemand {
        revenue = revenue == null ? BigDecimal.ZERO : revenue;
    }
}
