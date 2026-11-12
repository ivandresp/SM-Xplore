package com.smxplore.users.domain.exception;

/**
 * El alta del guía existe y la contraseña era correcta, pero una gestora todavía no la ha aprobado.
 *
 * <p>Solo se lanza después de verificar la contraseña: un atacante que no la conoce nunca ve este mensaje y, por
 * tanto, no puede usarlo para averiguar si el correo está registrado.
 */
public class AccountPendingReviewException extends DomainException {

    public AccountPendingReviewException() {
        super("Tu solicitud como guía está en revisión. Podrás iniciar sesión cuando una gestora la apruebe");
    }
}
