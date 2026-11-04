package com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.notification;

import java.time.Instant;
import java.util.UUID;

public record NotificationContactResponse(
        UUID userId,
        String email,
        Instant updatedAt
) {
}
