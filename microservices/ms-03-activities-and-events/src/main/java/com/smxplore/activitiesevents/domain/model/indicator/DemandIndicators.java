package com.smxplore.activitiesevents.domain.model.indicator;

import java.time.Instant;
import java.util.List;

/**
 * Demand indicators for activities and events. When a destination manager or an admin asks for
 * them, ms-03 also composes the service indicators exposed by ms-04, so the manager gets one view of
 * the whole destination. {@code partial} is true only when that cross-service call failed.
 */
public record DemandIndicators(
        Instant from,
        Instant to,
        DemandGranularity granularity,
        String timeZone,
        String scope,
        DemandSummary summary,
        DemandComparison comparison,
        List<DemandBucket> timeline,
        List<DemandDistribution> weekdays,
        List<DemandDistribution> hours,
        List<ZoneDemand> zones,
        List<SiteDemand> sites,
        List<ActivityDemand> activities,
        List<EventDemand> events,
        List<ServiceDemand> services,
        boolean partial,
        Instant generatedAt
) {

    public DemandIndicators {
        timeline = timeline == null ? List.of() : List.copyOf(timeline);
        weekdays = weekdays == null ? List.of() : List.copyOf(weekdays);
        hours = hours == null ? List.of() : List.copyOf(hours);
        zones = zones == null ? List.of() : List.copyOf(zones);
        sites = sites == null ? List.of() : List.copyOf(sites);
        activities = activities == null ? List.of() : List.copyOf(activities);
        events = events == null ? List.of() : List.copyOf(events);
        services = services == null ? List.of() : List.copyOf(services);
    }
}
