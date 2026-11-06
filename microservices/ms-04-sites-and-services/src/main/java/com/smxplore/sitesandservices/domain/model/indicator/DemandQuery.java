package com.smxplore.sitesandservices.domain.model.indicator;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Consulta interna de filas de reserva/ítem. {@code serviceIds == null} significa
 * "cualquier servicio"; una lista vacía significa "ninguno" y evita consultar la base.
 */
public record DemandQuery(
        Instant from,
        Instant to,
        UUID providerId,
        List<UUID> serviceIds
) {

    public DemandQuery {
        if (from == null || to == null) {
            throw new IllegalArgumentException("demand query from and to are required");
        }
        if (serviceIds != null && serviceIds.stream().anyMatch(id -> id == null)) {
            throw new IllegalArgumentException("demand query serviceIds cannot contain nulls");
        }
        serviceIds = serviceIds == null ? null : List.copyOf(serviceIds);
    }

    public boolean hasNoServices() {
        return serviceIds != null && serviceIds.isEmpty();
    }
}
