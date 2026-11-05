package com.smxplore.recommendations.domain.exception;

/**
 * The reindex request is malformed or the catalog export cannot be read.
 * Maps to HTTP 400.
 */
public class InvalidReindexException extends DomainValidationException {

    public InvalidReindexException(String message) {
        super(message);
    }

    public InvalidReindexException(String message, Throwable cause) {
        super(message, cause);
    }
}
