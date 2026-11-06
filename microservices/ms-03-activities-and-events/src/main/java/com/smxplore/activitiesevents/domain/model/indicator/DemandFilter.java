package com.smxplore.activitiesevents.domain.model.indicator;

import com.smxplore.activitiesevents.domain.exceptions.indicator.InvalidDemandFilterException;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Filter for activity/event demand indicators. {@code from} is inclusive and {@code to} is
 * exclusive, matching the reservation filters.
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
            throw new InvalidDemandFilterException("Demand indicator from and to are required.");
        }
        if (!to.isAfter(from)) {
            throw new InvalidDemandFilterException("Demand indicator to must be after from.");
        }
        if (Duration.between(from, to).compareTo(Duration.ofDays(MAX_RANGE_DAYS)) > 0) {
            throw new InvalidDemandFilterException(
                    "Demand indicator range cannot exceed " + MAX_RANGE_DAYS + " days.");
        }
        if (granularity == null) {
            granularity = DemandGranularity.DAY;
        }
    }

    public Instant previousFrom() {
        return from.minus(Duration.between(from, to));
    }
}
