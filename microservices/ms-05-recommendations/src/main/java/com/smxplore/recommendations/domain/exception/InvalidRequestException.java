package com.smxplore.recommendations.domain.exception;

public class InvalidRequestException extends DomainValidationException {

    public InvalidRequestException(String message) {
        super(message);
    }
}
