package com.smxplore.activitiesevents.domain.exceptions.reservation;

public class InvalidBookingDateException extends RuntimeException {
    public InvalidBookingDateException(String message) {
        super(message);
    }
}
