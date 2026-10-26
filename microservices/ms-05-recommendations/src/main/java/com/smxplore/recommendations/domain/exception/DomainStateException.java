package com.smxplore.recommendations.domain.exception;

/**
 * A broken domain invariant or malformed data coming from a downstream source.
 * Maps to HTTP 500 and must be logged.
 */
public class DomainStateException extends RuntimeException {

    public DomainStateException(String message) {
        super(message);
    }
}
