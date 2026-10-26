package com.smxplore.users.domain.exception;

public class InvalidCurrentPasswordException extends DomainException {

    public InvalidCurrentPasswordException() {
        super("La contraseña actual no es correcta");
    }
}
