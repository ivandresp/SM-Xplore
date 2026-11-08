package com.smxplore.sitesandservices.domain.exception;

/**
 * Limite de peticiones superado para una cuenta/token. Se mapea a 429.
 */
public class RateLimitExceededException extends RuntimeException {

    public RateLimitExceededException(String message) {
        super(message);
    }
}
