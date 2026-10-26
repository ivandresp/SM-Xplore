package com.smxplore.users.domain.exception;

public class InvalidCredentialsException extends DomainException {

    public InvalidCredentialsException() {
        super("Correo electrónico o contraseña incorrectos");
    }
}
