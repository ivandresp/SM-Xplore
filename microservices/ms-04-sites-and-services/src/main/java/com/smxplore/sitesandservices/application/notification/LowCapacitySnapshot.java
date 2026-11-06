package com.smxplore.sitesandservices.application.notification;

import java.time.Instant;
import java.util.UUID;

/**
 * Foto del cupo de un servicio en el momento de crear una reserva. {@code remainingBefore}
 * es el cupo disponible que vio la validación (antes de insertar) y {@code remainingAfter}
 * el que queda justo después de la reserva.
 */
public record LowCapacitySnapshot(
        UUID serviceId,
        String serviceName,
        UUID providerId,
        UUID managerId,
        int capacity,
        int remainingBefore,
        int remainingAfter,
        Instant bookedFor
) {
}
