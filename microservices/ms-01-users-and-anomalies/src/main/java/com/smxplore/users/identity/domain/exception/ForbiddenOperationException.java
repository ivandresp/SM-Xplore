package com.smxplore.users.identity.domain.exception;

public class ForbiddenOperationException extends DomainException {

    public ForbiddenOperationException(String message) {
        super(message);
    }
}
