package com.smxplore.users.domain.exception;

public class MfaNotEnabledException extends DomainException {

    public MfaNotEnabledException() {
        super("La autenticación multifactor no está activa para esta cuenta");
    }

    public MfaNotEnabledException(String message) {
        super(message);
    }
}
