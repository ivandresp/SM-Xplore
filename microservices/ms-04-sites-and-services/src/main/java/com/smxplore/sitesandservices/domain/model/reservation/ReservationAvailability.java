package com.smxplore.sitesandservices.domain.model.reservation;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Disponibilidad de un servicio. {@code maxCapacity} y {@code reservedPeople} son {@code null}
 * cuando quien consulta no es personal operativo: un turista solo recibe la disponibilidad
 * booleana y el motivo (MS04-08), nunca el cupo exacto.
 */
public record ReservationAvailability(
        UUID serviceId,
        String serviceName,
        UUID providerId,
        Instant bookedFor,
        LocalDate serviceDate,
        Integer maxCapacity,
        Integer reservedPeople,
        int requestedPeople,
        boolean available,
        String reason
) {

    public Integer remainingCapacity() {
        if (maxCapacity == null || reservedPeople == null) {
            return null;
        }
        return Math.max(0, maxCapacity - reservedPeople);
    }
}
