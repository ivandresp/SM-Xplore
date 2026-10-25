package com.smxplore.users.identity.domain.exception;

public class InvalidStatusTransitionException extends DomainException {

    public InvalidStatusTransitionException(String message) {
        super(message);
    }
}
