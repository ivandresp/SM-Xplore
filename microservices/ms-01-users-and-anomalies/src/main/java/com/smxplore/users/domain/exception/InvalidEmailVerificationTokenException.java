package com.smxplore.users.domain.exception;

public class InvalidEmailVerificationTokenException extends DomainException {

    public InvalidEmailVerificationTokenException() {
        super("El enlace de verificación de correo no es válido o ya expiró");
    }
}
