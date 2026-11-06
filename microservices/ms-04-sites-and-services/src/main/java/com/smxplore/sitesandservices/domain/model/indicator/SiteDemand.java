package com.smxplore.sitesandservices.domain.model.indicator;

import java.math.BigDecimal;
import java.util.UUID;

public record SiteDemand(
        UUID siteId,
        String siteName,
        UUID zoneId,
        String zoneName,
        long reservations,
        long people,
        BigDecimal revenue,
        double share,
        long capacityPeople,
        double occupancyRate
) {

    public SiteDemand {
        revenue = revenue == null ? BigDecimal.ZERO : revenue;
    }
}
