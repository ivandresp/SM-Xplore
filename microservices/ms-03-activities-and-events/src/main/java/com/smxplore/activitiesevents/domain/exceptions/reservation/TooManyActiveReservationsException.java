package com.smxplore.activitiesevents.domain.exceptions.reservation;

public class TooManyActiveReservationsException extends RuntimeException {
    public TooManyActiveReservationsException(String message) {
        super(message);
    }
}
