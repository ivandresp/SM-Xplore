package com.smxplore.users.domain.exception;

/**
 * El alta del guía fue rechazada por una gestora, así que la cuenta nunca podrá operar en la plataforma.
 *
 * <p>Igual que {@link AccountPendingReviewException}, solo se lanza después de verificar la contraseña.
 */
public class AccountRegistrationRejectedException extends DomainException {

    public AccountRegistrationRejectedException() {
        super("Tu solicitud como guía fue rechazada. Revisa el motivo en el correo que te enviamos");
    }
}
