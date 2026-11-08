package com.smxplore.securityreports.domain.ports.out;

/**
 * Politica de URLs de fotos (MS02-07): esquema y hosts permitidos.
 *
 * <p>Vive como puerto de dominio para que los casos de uso validen la politica antes de persistir, sin depender de
 * la configuracion de Spring. La implementacion por defecto lee {@code app.security.photos}.
 */
public interface PhotoUrlPolicyPort {

    /**
     * @throws com.smxplore.securityreports.domain.exceptions.InvalidReportRequestException si la URL no cumple la
     *                                                                                     politica.
     */
    void validate(String url);
}
