package com.smxplore.recommendations.domain.exception;

public class InvalidLimitException extends DomainValidationException {

    public InvalidLimitException(String message) {
        super(message);
    }
}
