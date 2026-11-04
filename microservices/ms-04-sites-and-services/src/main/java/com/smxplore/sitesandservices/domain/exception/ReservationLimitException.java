package com.smxplore.sitesandservices.domain.exception;

/**
 * A tourist exceeded an operational reservation quota or creation rate.
 * Maps to HTTP 429.
 */
public class ReservationLimitException extends RuntimeException {

    public ReservationLimitException(String message) {
        super(message);
    }
}
