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
    RECHAZADO
}
