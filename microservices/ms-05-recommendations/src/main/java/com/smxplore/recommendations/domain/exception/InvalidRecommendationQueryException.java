package com.smxplore.recommendations.domain.exception;

public class InvalidRecommendationQueryException extends DomainValidationException {

    public InvalidRecommendationQueryException(String message) {
        super(message);
    }
}
