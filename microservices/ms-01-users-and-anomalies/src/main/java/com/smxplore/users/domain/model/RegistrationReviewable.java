package com.smxplore.users.domain.model;

import java.time.Instant;

/**
 * Perfil de usuario cuyo alta queda sometida a la aprobación de una gestora.
 *
 * <p>Los guías y las prestadoras de servicios comparten la misma máquina de estados ({@link RegistrationStatus}) y el
 * mismo caso de uso de revisión; este contrato permite que ambos se revisen sin duplicar la lógica.
 */
public interface RegistrationReviewable {

    RegistrationStatus registrationStatus();

    /**
     * Cambia el estado del alta e invalida los tokens ya emitidos, porque el permiso de acceso depende de él.
     */
    void changeRegistrationStatus(RegistrationStatus newStatus, Instant now);
}
