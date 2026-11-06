package com.smxplore.sitesandservices.application.notification;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReservationChangedMessage(
        UUID eventId,
        UUID reservationId,
        UUID touristId,
        Instant bookedFor,
        String status,
        List<Item> items
) {

    public record Item(
            String itemType,
            UUID itemId,
            String itemName,
            UUID providerId,
            UUID guideId,
            UUID managerId,
            String serviceName,
            int people,
            BigDecimal total
    ) {
    }
}
