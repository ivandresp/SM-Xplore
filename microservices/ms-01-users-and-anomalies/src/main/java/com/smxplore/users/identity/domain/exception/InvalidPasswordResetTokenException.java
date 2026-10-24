package com.smxplore.users.identity.domain.exception;

public class InvalidPasswordResetTokenException extends DomainException {

    public InvalidPasswordResetTokenException() {
        super("El enlace de recuperación no es válido o ya expiró");
    }
}
