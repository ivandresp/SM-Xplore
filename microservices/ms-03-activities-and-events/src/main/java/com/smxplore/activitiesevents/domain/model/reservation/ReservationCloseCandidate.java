package com.smxplore.activitiesevents.domain.model.reservation;

import java.util.UUID;

/** A CONFIRMED reservation that the cleanup job must close and whose change must be published. */
public record ReservationCloseCandidate(UUID reservationId, ReservationStatus targetStatus) {
}
