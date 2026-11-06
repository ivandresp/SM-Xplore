package com.smxplore.users.domain.exception;

public class MfaAlreadyEnabledException extends DomainException {

    public MfaAlreadyEnabledException() {
        super("La autenticación multifactor ya está activa para esta cuenta");
    }
}
