package com.smxplore.users.domain.model;

/**
 * Estado del alta de un perfil que no puede operar hasta que una gestora lo apruebe.
 *
 * <p>Es una única máquina de estados compartida por los guías y las prestadoras de servicios: los valores no
 * dependen del rol, solo el perfil que los expone ({@link RegistrationReviewable}).
 */
public enum RegistrationStatus {
    PENDIENTE,
    APROBADO,
    RECHAZADO;

    /**
     * Una aprobación es terminal (para revocarla se cambia el estado del usuario, no el alta); un rechazo puede
     * reabrirse con una nueva revisión. La regla vive aquí, no en el servicio, para que cualquier llamador la respete.
     */
    public boolean canTransitionTo(RegistrationStatus next) {
        return switch (this) {
            case PENDIENTE -> next == APROBADO || next == RECHAZADO;
            case RECHAZADO -> next == APROBADO;
            case APROBADO -> false;
        };
    }
}
