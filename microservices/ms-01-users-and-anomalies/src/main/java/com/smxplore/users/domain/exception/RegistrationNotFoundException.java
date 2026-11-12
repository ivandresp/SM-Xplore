package com.smxplore.users.domain.exception;

/**
 * La cola de solicitudes no tiene un alta con ese identificador.
 *
 * <p>Se usa en la superficie de {@code /api/v1/registrations}: ahí el identificador designa una solicitud, así que un
 * id desconocido y uno de un perfil que no se revisa responden igual (404) y no confirman si la cuenta existe.
 */
public class RegistrationNotFoundException extends DomainException {

    public RegistrationNotFoundException() {
        super("No existe una solicitud de registro con ese identificador");
    }
}
