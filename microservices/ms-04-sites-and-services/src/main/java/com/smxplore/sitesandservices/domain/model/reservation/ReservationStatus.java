package com.smxplore.sitesandservices.domain.model.reservation;

public enum ReservationStatus {
    PENDING,
    CONFIRMED,
    CANCELLED,
    COMPLETED;

    public boolean holdsCapacity() {
        return this == PENDING || this == CONFIRMED;
    }

    public boolean canTransitionTo(ReservationStatus next) {
        if (next == null || next == this) {
            return false;
        }
        return switch (this) {
            // PENDING se conserva solo para datos heredados; las reservas nuevas nacen CONFIRMED.
            case PENDING -> next == CANCELLED;
            case CONFIRMED -> next == CANCELLED || next == COMPLETED;
            case CANCELLED, COMPLETED -> false;
        };
    }
}
