package com.smxplore.recommendations.domain.exception;

public class InvalidScoringException extends DomainStateException {

    public InvalidScoringException(String message) {
        super(message);
    }
}
