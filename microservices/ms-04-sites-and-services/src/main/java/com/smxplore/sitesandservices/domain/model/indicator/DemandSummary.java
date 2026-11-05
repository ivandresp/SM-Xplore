package com.smxplore.sitesandservices.domain.model.indicator;

import java.math.BigDecimal;

public record DemandSummary(
        long reservations,
        long people,
        BigDecimal revenue,
        long cancelledReservations,
        long completedReservations,
        long uniqueTourists,
        double cancellationRate,
        double averagePeoplePerReservation
) {

    public DemandSummary {
        revenue = revenue == null ? BigDecimal.ZERO : revenue;
    }

    public static DemandSummary empty() {
        return new DemandSummary(0, 0, BigDecimal.ZERO, 0, 0, 0, 0.0, 0.0);
    }
}
