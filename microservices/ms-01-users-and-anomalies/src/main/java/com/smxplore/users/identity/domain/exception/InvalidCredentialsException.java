package com.smxplore.users.identity.domain.exception;

public class InvalidCredentialsException extends DomainException {

    public InvalidCredentialsException() {
        super("Correo electrónico o contraseña incorrectos");
    }
}
