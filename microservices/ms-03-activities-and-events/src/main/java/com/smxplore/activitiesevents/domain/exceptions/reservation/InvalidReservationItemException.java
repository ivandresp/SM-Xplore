package com.smxplore.activitiesevents.domain.exceptions.reservation;

public class InvalidReservationItemException extends RuntimeException {
    public InvalidReservationItemException(String message) {
        super(message);
    }
}
