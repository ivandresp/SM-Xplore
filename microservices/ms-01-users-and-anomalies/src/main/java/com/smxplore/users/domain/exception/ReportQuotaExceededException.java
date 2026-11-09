package com.smxplore.users.domain.exception;

import java.time.Duration;

/**
 * MS01-22: cuota de reportes de reseñas agotada.
 *
 * <p>Cada turista puede reportar un número acotado de reseñas por ventana para evitar que el buzón de moderación se
 * inunde y desplace los reportes legítimos.
 */
public class ReportQuotaExceededException extends DomainException {

    private final Duration retryAfter;

    public ReportQuotaExceededException(Duration retryAfter) {
        super("Has alcanzado el límite de reportes de reseñas; vuelve a intentarlo más tarde");
        this.retryAfter = retryAfter;
    }

    public Duration retryAfter() {
        return retryAfter;
    }
}
