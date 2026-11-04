package com.smxplore.users.domain.exception;

public class AccountNotActiveException extends DomainException {

    public AccountNotActiveException() {
        super("La cuenta no se encuentra activa");
    }
}
