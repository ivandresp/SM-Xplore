package com.smxplore.activitiesevents.domain.model.indicator;

import com.smxplore.activitiesevents.domain.model.reservation.ReservationItemType;
import com.smxplore.activitiesevents.domain.model.reservation.ReservationStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Flat projection of a reservation joined with its activity or event. The service aggregates it in
 * memory so catalog dimensions (site, zone, guide, manager) never require a cross-database join.
 */
public record DemandRecord(
        UUID reservationId,
        UUID touristId,
        ReservationStatus status,
        Instant bookedFor,
        ReservationItemType itemType,
        UUID itemId,
        String itemName,
        UUID siteId,
        String siteName,
        UUID guideId,
        UUID managerId,
        int expectedAttendance,
        int maxCapacity,
        String category,
        int people,
        BigDecimal itemTotal
) {

    public boolean isCancelled() {
        return status == ReservationStatus.CANCELLED;
    }

    public boolean isCompleted() {
        return status == ReservationStatus.COMPLETED;
    }
}
