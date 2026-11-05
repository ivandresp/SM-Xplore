package com.smxplore.activitiesevents.domain.model.indicator;

import java.math.BigDecimal;
import java.util.UUID;

/** Service demand fetched from ms-04 when a destination manager asks for the consolidated view. */
public record ServiceDemand(
        UUID serviceId,
        String serviceName,
        String serviceType,
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

    public ServiceDemand {
        revenue = revenue == null ? BigDecimal.ZERO : revenue;
    }
}
