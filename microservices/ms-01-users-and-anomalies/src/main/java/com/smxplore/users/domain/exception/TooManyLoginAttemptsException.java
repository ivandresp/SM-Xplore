package com.smxplore.users.domain.exception;

import java.time.Duration;

public class TooManyLoginAttemptsException extends DomainException {

    private final Duration retryAfter;

    public TooManyLoginAttemptsException(Duration retryAfter) {
        super("Demasiados intentos fallidos; vuelve a intentarlo más tarde");
        this.retryAfter = retryAfter;
    }

    public Duration retryAfter() {
        return retryAfter;
    }
}
