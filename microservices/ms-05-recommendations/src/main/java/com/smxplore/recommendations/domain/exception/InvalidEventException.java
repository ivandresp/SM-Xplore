package com.smxplore.recommendations.domain.exception;

/**
 * A Kafka event does not satisfy the published contract or contains invalid data.
 * It is not retryable: the message must be inspected and corrected by the producer.
 */
public class InvalidEventException extends DomainStateException {

    public InvalidEventException(String message) {
        super(message);
    }

    public InvalidEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
