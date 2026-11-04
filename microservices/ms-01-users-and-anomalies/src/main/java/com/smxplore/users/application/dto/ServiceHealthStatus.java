package com.smxplore.users.application.dto;

import com.smxplore.users.domain.exception.ValidationException;

/**
 * Resultado de una verificación de disponibilidad de las dependencias del servicio.
 *
 * @param available si PostgreSQL y MongoDB respondieron correctamente
 * @param consecutiveFailures cantidad de verificaciones fallidas consecutivas
 * @param detail detalle de las dependencias que fallaron
 */
public record ServiceHealthStatus(boolean available, int consecutiveFailures, String detail) {

    public ServiceHealthStatus {
        if (consecutiveFailures < 0) {
            throw new ValidationException("La cantidad de fallos consecutivos no puede ser negativa");
        }
        detail = detail == null ? "" : detail.trim();
    }

    public static ServiceHealthStatus healthy() {
        return new ServiceHealthStatus(true, 0, "");
    }

    public static ServiceHealthStatus unavailable(int consecutiveFailures, String detail) {
        return new ServiceHealthStatus(false, consecutiveFailures, detail);
    }
}
