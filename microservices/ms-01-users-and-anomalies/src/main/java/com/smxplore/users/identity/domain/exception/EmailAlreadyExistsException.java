package com.smxplore.users.identity.domain.exception;

public class EmailAlreadyExistsException extends DomainException {

    public EmailAlreadyExistsException() {
        super("Ya existe una cuenta registrada con ese correo electrónico");
    }
}
