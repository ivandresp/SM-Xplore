package com.smxplore.recommendations.domain.exception;

public class InvalidPreferenceException extends DomainValidationException {

    public InvalidPreferenceException(String message) {
        super(message);
    }
}
