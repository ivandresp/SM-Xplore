package com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.reservation;

import java.math.BigDecimal;
import java.util.UUID;

public record ReservationItemResponse(
        UUID reservationItemId,
        UUID serviceId,
        String serviceName,
        UUID providerId,
        String providerName,
        String serviceType,
        BigDecimal unitPrice,
        int people,
        BigDecimal total,
        int position
) {
}
