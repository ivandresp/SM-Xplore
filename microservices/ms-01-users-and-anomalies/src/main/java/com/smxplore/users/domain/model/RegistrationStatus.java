package com.smxplore.users.domain.model;

/**
 * Estado de aprobación del registro de guías y gestoras de destinos.
 *
 * <p>El auto-registro público nace {@link #PENDIENTE}: la cuenta puede iniciar sesión y verificar su correo, pero la
 * aprobación la concede una gestora de destinos (guías) o un administrador (gestoras). Las cuentas creadas por un
 * administrador nacen aprobadas.
 */
public enum RegistrationStatus {
    PENDIENTE,
    APROBADO,
    RECHAZADO
}
