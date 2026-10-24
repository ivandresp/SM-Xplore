package com.smxplore.users.identity.domain.exception;

public class UserNotFoundException extends DomainException {

    public UserNotFoundException() {
        super("No se encontró el usuario solicitado");
    }
}
