package com.smxplore.activitiesevents.domain.model.reservation;

import java.util.Set;

public enum ReservationStatus {
    CONFIRMED,
    CANCELLED,
    NO_SHOW,
    COMPLETED;

    public Set<ReservationStatus> allowedTransitions() {
        return switch (this) {
            case CONFIRMED -> Set.of(CANCELLED, NO_SHOW, COMPLETED);
            case CANCELLED, NO_SHOW, COMPLETED -> Set.of();
        };
    }

    public boolean canTransitionTo(ReservationStatus target) {
        return allowedTransitions().contains(target);
    }

    public boolean isFinal() {
        return allowedTransitions().isEmpty();
    }
}
