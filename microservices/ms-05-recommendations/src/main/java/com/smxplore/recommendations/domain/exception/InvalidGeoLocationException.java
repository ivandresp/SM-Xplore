package com.smxplore.recommendations.domain.exception;

public class InvalidGeoLocationException extends DomainValidationException {

    public InvalidGeoLocationException(String message) {
        super(message);
    }
}
