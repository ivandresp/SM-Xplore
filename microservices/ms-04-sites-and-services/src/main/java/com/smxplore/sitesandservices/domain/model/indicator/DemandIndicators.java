package com.smxplore.sitesandservices.domain.model.indicator;

import java.time.Instant;
import java.util.List;

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
        List<ServiceDemand> services,
        Instant generatedAt
) {

    public DemandIndicators {
        timeline = timeline == null ? List.of() : List.copyOf(timeline);
        weekdays = weekdays == null ? List.of() : List.copyOf(weekdays);
        hours = hours == null ? List.of() : List.copyOf(hours);
        zones = zones == null ? List.of() : List.copyOf(zones);
        sites = sites == null ? List.of() : List.copyOf(sites);
        services = services == null ? List.of() : List.copyOf(services);
    }
}
