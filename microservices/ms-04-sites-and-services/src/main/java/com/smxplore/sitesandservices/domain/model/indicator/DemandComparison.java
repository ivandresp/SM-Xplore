package com.smxplore.sitesandservices.domain.model.indicator;

public record DemandComparison(
        DemandSummary current,
        DemandSummary previous,
        double reservationsChange,
        double peopleChange,
        double revenueChange,
        double cancellationRateChange
) {
}
