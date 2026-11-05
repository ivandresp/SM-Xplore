package com.smxplore.sitesandservices.domain.model.indicator;

import com.smxplore.sitesandservices.domain.exception.InvalidCatalogException;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Filtro de consulta de indicadores. {@code from} es inclusivo y {@code to}
 * exclusivo, igual que los filtros de reservas.
 */
public record DemandFilter(
        Instant from,
        Instant to,
        UUID zoneId,
        UUID siteId,
        DemandGranularity granularity
) {

    public static final int MAX_RANGE_DAYS = 366;

    public DemandFilter {
        if (from == null || to == null) {
            throw new InvalidCatalogException("demand indicator from and to are required");
        }
        if (!to.isAfter(from)) {
            throw new InvalidCatalogException("demand indicator to must be after from");
        }
        if (Duration.between(from, to).compareTo(Duration.ofDays(MAX_RANGE_DAYS)) > 0) {
            throw new InvalidCatalogException(
                    "demand indicator range cannot exceed " + MAX_RANGE_DAYS + " days");
        }
        if (granularity == null) {
            granularity = DemandGranularity.DAY;
        }
    }

    public Instant previousFrom() {
        return from.minus(Duration.between(from, to));
    }
}
