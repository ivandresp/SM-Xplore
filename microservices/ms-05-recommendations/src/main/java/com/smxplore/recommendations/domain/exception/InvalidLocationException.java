package com.smxplore.recommendations.domain.exception;

public class InvalidLocationException extends DomainValidationException {

    public InvalidLocationException(String message) {
        super(message);
    }
}
