package com.smxplore.activitiesevents.domain.exceptions.reservation;

public class InvalidReservationRequestException extends RuntimeException {
    public InvalidReservationRequestException(String message) {
        super(message);
    }
}
