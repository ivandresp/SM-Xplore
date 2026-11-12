package com.smxplore.users.application.dto;

import com.smxplore.users.domain.model.RegistrationStatus;

/**
 * Decisión de revisión del registro de una guía o de una gestora de destinos.
 *
 * <p>Solo se admiten {@link RegistrationStatus#APROBADO} y {@link RegistrationStatus#RECHAZADO}; la validación se
 * hace en los servicios de aplicación para alinear el error con el resto del módulo.
 */
public record ReviewRegistrationCommand(RegistrationStatus decision, String reason) {}
