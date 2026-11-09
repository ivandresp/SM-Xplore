package com.smxplore.recommendations.domain.exception;

/**
 * Raised when a caller exceeds its request budget. Mapped to HTTP 429 with a
 * {@code Retry-After} header by the web layer (MS05-02).
 */
public class RateLimitExceededException extends RuntimeException {

    private final long retryAfterSeconds;

    public RateLimitExceededException(String message, long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
