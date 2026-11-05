package com.smxplore.activitiesevents.domain.model.indicator;

public record DemandDistribution(
        String key,
        String label,
        long reservations,
        long people
) {
}
