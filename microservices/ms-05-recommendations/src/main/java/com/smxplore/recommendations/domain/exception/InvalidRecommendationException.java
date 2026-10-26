package com.smxplore.recommendations.domain.exception;

public class InvalidRecommendationException extends DomainStateException {

    public InvalidRecommendationException(String message) {
        super(message);
    }
}
