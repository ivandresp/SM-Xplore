package com.smxplore.users.domain.model;

import java.time.Instant;

/**
 * Perfil de usuario cuyo alta queda sometida a la aprobación de una gestora.
 *
 * <p>Los guías y las prestadoras de servicios comparten la misma máquina de estados ({@link RegistrationStatus}) y el
 * mismo caso de uso de revisión; este contrato permite que ambos se revisen sin duplicar la lógica.
 *
 * <p>El estado decide la <strong>exposición pública</strong> del perfil: mientras no esté aprobado no se publica, no
 * admite reseñas nuevas y sus reseñas no se sirven. En los guías además condiciona el inicio de sesión; en las
 * prestadoras no: una prestadora rechazada conserva el acceso y solo pierde la publicación (asimetría aceptada).
 */
public interface RegistrationReviewable {

    RegistrationStatus registrationStatus();

    /**
     * Cambia el estado del alta e invalida los tokens ya emitidos (incrementa {@code authVersion}), porque la decisión
     * puede cambiar lo que el titular ve y hace. No implica por sí mismo que el titular deje de poder iniciar sesión:
     * eso depende del rol (ver el comentario del tipo).
     */
    void changeRegistrationStatus(RegistrationStatus newStatus, Instant now);

    /**
     * Regla única de publicación: un alta solo es pública cuando está aprobada.
     */
    static boolean isApproved(Usuario user) {
        return user instanceof RegistrationReviewable reviewable
                && reviewable.registrationStatus() == RegistrationStatus.APROBADO;
    }
}
