package com.smxplore.sitesandservices.domain.model.indicator;

import com.smxplore.sitesandservices.domain.model.reservation.ReservationStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Proyección plana de una fila reserva + ítem. El servicio la agrega en memoria para
 * combinar las dimensiones de catálogo (zona, sitio, servicio) sin hacer joins entre
 * PostgreSQL y MongoDB.
 */
public record DemandRecord(
        UUID reservationId,
        UUID touristId,
        ReservationStatus status,
        Instant bookedFor,
        UUID serviceId,
        UUID providerId,
        String serviceName,
        String serviceType,
        int people,
        BigDecimal itemTotal
) {

    public boolean isCancelled() {
        return status == ReservationStatus.CANCELLED;
    }

    public boolean isCompleted() {
        return status == ReservationStatus.COMPLETED;
    }
}
