package com.smxplore.recommendations.domain.exception;

/**
 * Invalid input supplied by a caller. Maps to HTTP 400.
 */
public class DomainValidationException extends RuntimeException {

    public DomainValidationException(String message) {
        super(message);
    }
}
