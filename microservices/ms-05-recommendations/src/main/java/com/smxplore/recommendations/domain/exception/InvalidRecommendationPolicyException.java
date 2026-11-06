package com.smxplore.recommendations.domain.exception;

public class InvalidRecommendationPolicyException extends DomainStateException {

    public InvalidRecommendationPolicyException(String message) {
        super(message);
    }
}
