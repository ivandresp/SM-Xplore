package com.smxplore.activitiesevents.domain.model.reservation;

import java.util.Set;

public enum ReservationStatus {
    PENDING,
    CONFIRMED,
    CANCELLATION_REQUESTED,
    REJECTED,
    CANCELLED,
    NO_SHOW,
    COMPLETED;

    public Set<ReservationStatus> allowedTransitions() {
        return switch (this) {
            case PENDING -> Set.of(CONFIRMED, REJECTED, CANCELLED);
            case CONFIRMED -> Set.of(CANCELLATION_REQUESTED, CANCELLED, NO_SHOW, COMPLETED);
            case CANCELLATION_REQUESTED -> Set.of(CANCELLED, CONFIRMED);
            case REJECTED, CANCELLED, NO_SHOW, COMPLETED -> Set.of();
        };
    }

    public boolean canTransitionTo(ReservationStatus target) {
        return allowedTransitions().contains(target);
    }

    public boolean isFinal() {
        return allowedTransitions().isEmpty();
    }
}