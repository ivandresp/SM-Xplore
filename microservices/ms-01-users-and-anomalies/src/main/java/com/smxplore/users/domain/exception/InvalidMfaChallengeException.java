package com.smxplore.users.domain.exception;

public class InvalidMfaChallengeException extends DomainException {

    public InvalidMfaChallengeException() {
        super("El desafío de verificación expiró o no es válido; inicia sesión nuevamente");
    }
}
