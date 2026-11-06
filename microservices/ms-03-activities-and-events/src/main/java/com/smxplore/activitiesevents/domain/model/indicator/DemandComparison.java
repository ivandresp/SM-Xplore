package com.smxplore.activitiesevents.domain.model.indicator;

public record DemandComparison(
        DemandSummary current,
        DemandSummary previous,
        double reservationsChange,
        double peopleChange,
        double revenueChange,
        double cancellationRateChange
) {
}
