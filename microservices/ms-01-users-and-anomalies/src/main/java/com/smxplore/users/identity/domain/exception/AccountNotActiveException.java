package com.smxplore.users.identity.domain.exception;

public class AccountNotActiveException extends DomainException {

    public AccountNotActiveException() {
        super("La cuenta no se encuentra activa");
    }
}
