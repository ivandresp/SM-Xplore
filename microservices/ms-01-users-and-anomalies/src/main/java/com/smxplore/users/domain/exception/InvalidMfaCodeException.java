package com.smxplore.users.domain.exception;

public class InvalidMfaCodeException extends DomainException {

    public InvalidMfaCodeException() {
        super("El código de verificación no es válido");
    }
}
