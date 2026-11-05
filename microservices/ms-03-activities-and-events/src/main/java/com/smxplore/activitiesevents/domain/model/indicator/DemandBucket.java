package com.smxplore.activitiesevents.domain.model.indicator;

import java.math.BigDecimal;

public record DemandBucket(
        String period,
        long reservations,
        long people,
        BigDecimal revenue
) {

    public DemandBucket {
        revenue = revenue == null ? BigDecimal.ZERO : revenue;
    }
}
